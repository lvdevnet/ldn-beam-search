@Grapes([
    @Grab(group='redis.clients', module='jedis', version='2.7.3'),
    @Grab(group='org.codehaus.gpars', module='gpars', version='1.2.1')])

import groovy.transform.CompileStatic
import static groovyx.gpars.GParsPool.withPool
import redis.clients.jedis.Jedis

class Loader {
    File dir
    boolean cluster

    Loader(String _dir, boolean _cluster) {
        dir = new File(_dir)
        cluster = _cluster
    }

    String _token = 't:'
    String _content = 'c:'
    void load() {
        def files = []
        dir.eachFile(groovy.io.FileType.FILES) { file ->
            if (file.name.endsWith('.txt'))
                files << file
        }
        int total = withPool(files.size()) {
            [files, files.indices].transpose().collectParallel { file, index ->
                int loaded = 0
                Jedis redis = new Jedis('localhost', 6379 + cluster ? index : 0, 10_000) // 10 sec
                def pipe = redis.pipelined()
                file.withReader('UTF-8') { reader ->
                    while (true) {
                        String id = reader.readLine()
                        if (id == null) break
                        String title = reader.readLine()
                        String[] tokens = reader.readLine().split(' ')
                      //pipe.sadd(_content + id, *tokens)
                        pipe.set(_content + id, title)
                        tokens.each { pipe.sadd(_token + it, id) }
                        pipe.sync()
                        ++loaded
                        if (loaded % 1000 == 0) print('.')
                    }
                }
                loaded
            } .sum()
        }
        println("\nloaded $total articles")
    }
}

new Loader('txt', false).load()
