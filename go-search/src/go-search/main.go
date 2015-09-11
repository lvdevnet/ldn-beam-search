package main

import (
	"fmt"
	"github.com/mediocregopher/radix.v2/redis"
	"log"
	"net/http"
	"strings"
	"time"
)

var rds *redis.Client

func main() {
	var err error
	rds, err = redis.Dial("tcp", "localhost:6379")
	if err != nil {
		log.Fatal(err)
	}

	http.HandleFunc("/content", search)
	s := &http.Server{
		Addr:         ":4080",
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}
	log.Fatal(s.ListenAndServe())
}

const (
	_token   = "t:"
	_content = "c:"
)

func search(w http.ResponseWriter, r *http.Request) {
	w.Header().Add("Server", "go-search")
	q := r.FormValue("q")
	if q == "" {
		fmt.Fprint(w, "Usage: /content?q=ask+me")
		return
	}
	words := strings.Split(q, " ")
	tokens := make([]string, len(words))
	for i, word := range words {
		tokens[i] = _token + word
	}
	rIds := rds.Cmd("SINTER", tokens)
	if rIds.Err != nil {
		http.Error(w, fmt.Sprintf("Redis error: %v", rIds.Err), http.StatusInternalServerError)
		return
	}
	lIds, _ := rIds.List()
	for _, id := range lIds {
		rContent := rds.Cmd("GET", _content+id)
		var content string
		if rIds.Err != nil {
			content = fmt.Sprintf("(redis error: %v)", rIds.Err)
		} else {
			content, _ = rContent.Str()
		}
		fmt.Fprintf(w, "%v,%v\n", id, content)
	}
}
