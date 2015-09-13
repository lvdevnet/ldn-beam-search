@Grapes([
    //@Grab(group='it.unimi.dsi', module='fastutil', version='7.0.7'),
    @Grab(group='redis.clients', module='jedis', version='2.7.3'),
    @Grab(group='org.codehaus.gpars', module='gpars', version='1.2.1')])

import groovy.transform.CompileStatic
import static groovyx.gpars.GParsPool.withPool
import redis.clients.jedis.Jedis
import redis.clients.jedis.Pipeline

class Loader {
    File dir
    boolean cluster
    int limit, cutoff

    Loader(String _dir, boolean _cluster, int _limit, int _cutoff) {
        dir = new File(_dir)
        cluster = _cluster
        limit = _limit; cutoff = _cutoff
    }

    @CompileStatic
    private void progress(int i) {
        if (i % 10_000 == 0) print(i)
        else if (i % 1_000 == 0) print('.')
    }

    String _token = 't:'
    String _content = 'c:'
    void load() {
        List<File> files = []
        dir.eachFile(groovy.io.FileType.FILES) { File file ->
            if (file.name.endsWith('.txt'))
                files << file
        }
        // collect unique words
        println('reading 1st pass')
        Map<String, Integer> unique = [:]
        int read = 0
        files.each { File file ->
            file.withReader { Reader reader ->
                if (read < limit) {
                    while (true) {
                        String id = reader.readLine()
                        if (id == null) break
                        String title = reader.readLine()
                        String[] words = reader.readLine().split(' ')
                        words.each { String word -> unique[word] = (unique[word] ?: 0) + 1 }
                        progress(read++)
                        if (read >= limit) break
                    }
                }
            }
        }
        println(".$read")
        println("total ${unique.size()} unique words")
        unique = unique.findAll { k, v -> v >= cutoff }
        println("will load ${unique.size()} unique words")
        // load into Redis
        int nthreads = files.size()
        int tlimit = limit.intdiv(nthreads) ?: 1
        int total = withPool(nthreads) {
            [files, files.indices].transpose().collectParallel { File file, int index ->
                int loaded = 0
                Jedis redis = new Jedis('localhost', 6379 + (cluster ? index : 0), 10_000) // 10 sec
                Pipeline pipe = redis.pipelined()
                file.withReader('UTF-8') { Reader reader ->
                    while (true) {
                        String id = reader.readLine()
                        if (id == null) break
                        String title = reader.readLine()
                        String[] words = reader.readLine().split(' ')
                        words = words.findAll { String word -> unique.containsKey(word) }
                        if (words) {
                            pipe.set(_content + id, title)
                            words.each { pipe.sadd(_token + it, id) }
                            pipe.sync()
                            if (loaded++ % 1_000 == 0) print('.')
                            if (loaded >= tlimit) break
                        }
                    }
                }
                loaded
            } .sum()
        }
        println("\nloaded $total articles")
    }
}

new Loader('../dump-converter/txt', true, 100_000, 50).load()
