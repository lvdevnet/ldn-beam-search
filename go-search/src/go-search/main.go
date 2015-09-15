package main

import (
	"bufio"
	"flag"
	"fmt"
	radix "github.com/mediocregopher/radix.v2/pool"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path"
	"runtime"
	"runtime/debug"
	"sort"
	"strings"
	"time"
)

func main() {
	var redis *radix.Pool
	var indices []string
	var titles map[string]string
	var bitmaps map[string][]uint64

	articles := flag.String("articles", "", "Directory to load *.txt files from")
	limit := flag.Uint("limit", 0, "Max number of articles to read")
	cutoff := flag.Uint("cutoff", 10, "Number of articles a word must appear into to be included in index")
	memlim := flag.Uint("memory", 0, "Target index final memory consumption in MB")
	flag.Parse()

	var bindAddr string
	if *articles != "" {
		indices, titles, bitmaps = loadArticles(*articles, *limit, *cutoff, *memlim)
		bindAddr = ":4088"
	} else {
		var err error
		redis, err = radix.New("tcp", "localhost:6379", 10)
		if err != nil {
			log.Fatal(err)
		}
		bindAddr = ":4080"
	}

	http.HandleFunc("/content", func(w http.ResponseWriter, r *http.Request) {
		q := r.FormValue("q")
		if q == "" {
			fmt.Fprint(w, "Usage: /content?q=ask+me")
			return
		}
		words := strings.Split(q, " ")
		if redis != nil {
			askRedis(w, words, redis)
		} else {
			searchMemory(w, words, indices, titles, bitmaps)
		}
	})
	s := &http.Server{
		Addr:         bindAddr,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}
	log.Print("ready\n")
	log.Fatal(s.ListenAndServe())
}

func askRedis(w http.ResponseWriter, words []string, pool *radix.Pool) {
	w.Header().Add("Server", "go-search/redis")
	const (
		_token   = "t:"
		_content = "c:"
	)
	tokens := make([]string, len(words))
	for i, word := range words {
		tokens[i] = _token + word
	}
	redis, err := pool.Get()
	if err != nil {
		http.Error(w, fmt.Sprintf("Redis error: %v", err), http.StatusInternalServerError)
		// do not return connection into the pool
		return
	}
	rIds := redis.Cmd("SINTER", tokens)
	if rIds.Err != nil {
		http.Error(w, fmt.Sprintf("Redis error: %v", rIds.Err), http.StatusInternalServerError)
		// do not return connection into the pool
		return
	}
	lIds, _ := rIds.List()
	for _, id := range lIds {
		rContent := redis.Cmd("GET", _content+id)
		var title string
		if rIds.Err != nil {
			title = fmt.Sprintf("(redis error: %v)", rIds.Err)
		} else {
			title, _ = rContent.Str()
		}
		fmt.Fprintf(w, "%v,%v\n", id, title)
	}
	pool.Put(redis)
}

func searchMemory(w http.ResponseWriter, words []string, indices []string, titles map[string]string, bitmaps map[string][]uint64) {
	w.Header().Add("Server", "go-search/memory")
	var answer []uint64
	for _, word := range words {
		bitmap, exist := bitmaps[word]
		if !exist {
			return
		}
		if answer == nil {
			answer = make([]uint64, len(bitmap))
			copy(answer, bitmap)
		} else {
			bitand(answer, bitmap)
		}
	}
	if answer != nil {
		for _, index := range bitsToIndices(answer) {
			id := indices[index]
			title := titles[id]
			fmt.Fprintf(w, "%v,%v\n", id, title)
		}
	}
}

func bitand(left []uint64, right []uint64) {
	for i, r := range right {
		left[i] &= r
	}
}

func bitsToIndices(bitmap []uint64) []uint {
	indices := make([]uint, 0, 10)
	for i, val := range bitmap {
		if val > 0 {
			for j := 0; j < 64; j++ {
				var mask uint64 = 1 << uint(j)
				if val&mask > 0 {
					indices = append(indices, uint(i*64+j))
				}
			}
		}
	}
	return indices
}

func readLine(reader *bufio.Reader) string {
	line, err := reader.ReadString('\n')
	if err == io.EOF {
		return ""
	} else if err != nil {
		log.Fatal(err)
	}
	return strings.TrimSuffix(line, "\n")
}

func readArticle(reader *bufio.Reader) (string, string, string) {
	id := readLine(reader)
	if id == "" {
		return "", "", ""
	}
	title := readLine(reader)
	if title == "" {
		return "", "", ""
	}
	content := readLine(reader)
	if content == "" {
		return "", "", ""
	}
	return id, title, content
}

func progress(i uint) {
	if i%10000 == 0 {
		fmt.Print(i)
	} else if i%1000 == 0 {
		fmt.Print(".")
	}
}

func loadArticles(articles string, limit, cutoff, memlim uint) ([]string, map[string]string, map[string][]uint64) {
	// prepare list of text files
	_files, err := ioutil.ReadDir(articles)
	if err != nil {
		log.Fatal(err)
	}
	files := make([]string, 0, len(_files))
	for _, finfo := range _files {
		if !finfo.IsDir() && strings.HasSuffix(finfo.Name(), ".txt") {
			files = append(files, finfo.Name())
		}
	}

	// make an iterator over text files that invokes callback for every article
	// TODO convert to channels?
	iterate := func(process func(string, string, string) bool) {
	done:
		for _, filename := range files {
			file, err := os.Open(path.Join(articles, filename))
			if err != nil {
				log.Fatal(err)
			}
			reader := bufio.NewReader(file)
			for {
				id, title, content := readArticle(reader)
				if id == "" {
					break
				}
				if process(id, title, content) {
					break done
				}
			}
			file.Close()
		}
	}

	// read files to find total number of articles and prepare a map of [word -> number of occurences]
	log.Print("reading 1st pass\n")
	var nArticles uint = 0
	unique := make(map[string]uint)
	iterate(
		func(id, title, content string) bool {
			words := strings.Split(content, " ")
			for _, word := range words {
				count, exist := unique[word]
				if exist {
					count += 1
				} else {
					count = 1
				}
				unique[word] = count
			}
			progress(nArticles)
			nArticles += 1
			if limit > 0 && nArticles >= limit {
				return true
			}
			return false
		})
	fmt.Printf(".%v\n", nArticles)
	log.Printf("total %v unique words\n", len(unique))

	if nArticles <= cutoff {
		cutoff /= 10
	}

	indices := make([]string, nArticles)         // article index -> article id
	titles := make(map[string]string, nArticles) // article id -> article title
	bitmaps := make(map[string][]uint64)         // word -> bitmap of 'word presence' in articles, where every bit maps to article index
	bitmapSize := nArticles/64 + 1

	// calculate memory limit if asked to
	wordlim := 0
	if memlim > 0 {
		wordlim = int(uint64(memlim) * 1024 * 1024 / uint64(bitmapSize*10 /*8*1.3*/))
		log.Printf("memory limit is %v words", wordlim)
	}

	// initialize words bitmaps based on limits
	for i, p := range sortByValue(unique) {
		if p.Value < cutoff || (wordlim > 0 && i >= wordlim) {
			break
		}
		bitmaps[p.Key] = make([]uint64, bitmapSize)
	}
	unique = nil
	log.Printf("will load %v unique words", len(bitmaps))
	runtime.GC()

	// read files and fill bitmaps
	var index uint = 0
	iterate(
		func(id, title, content string) bool {
			addr := index / 64
			bit := index % 64
			var mask uint64 = 1 << bit
			words := strings.Split(content, " ")
			articleLoaded := false
			for _, word := range words {
				bitmap, exist := bitmaps[word]
				if exist {
					bitmap[addr] |= mask
					articleLoaded = true
				}
			}
			if (articleLoaded) {
				indices[index] = id
				titles[id] = title
				progress(index)
				index += 1
				if index >= nArticles {
					return true
				}
			}
			return false
		})

	fmt.Printf(".%v\n", index)
	log.Printf("loaded %v articles", index)

	debug.FreeOSMemory()
	return indices, titles, bitmaps
}

// sort map[string]int by value and return []KV in descending order
type KV struct {
	Key   string
	Value uint
}

type KVList []KV

func (p KVList) Swap(i, j int)      { p[i], p[j] = p[j], p[i] }
func (p KVList) Len() int           { return len(p) }
func (p KVList) Less(i, j int) bool { return p[j].Value < p[i].Value }

func sortByValue(m map[string]uint) KVList {
	p := make(KVList, len(m))
	i := 0
	for k, v := range m {
		p[i] = KV{k, v}
		i += 1
	}
	sort.Sort(p)
	return p
}
