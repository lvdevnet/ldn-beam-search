@Grapes([
    @Grab(group='redis.clients', module='jedis', version='2.7.3'),
    @Grab(group='org.codehaus.gpars', module='gpars', version='1.2.1')])

import groovy.transform.CompileStatic

import groovyx.gpars.actor.Actor
import static groovyx.gpars.actor.Actors.actor
import redis.clients.jedis.Jedis
import org.xml.sax.Attributes
import org.xml.sax.SAXException

class Loader {
    String xml
    Jedis redis = new Jedis('localhost', 6379, 20_000) // 20 sec
    def stopwords = Stopwords.instance.words

    Loader(String _xml) {
        xml = _xml
    }

    void load() {
        javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().parse(xml, new Parser())
    }

    void document(String id, String title, String text) {
        if (makesense(text))
            tokenizer << [id, title, text]
    }

    boolean makesense(String text) {
        !text.startsWith('#REDIRECT ')
    }

    def tokenizer = actor {
        loop {
            react { msg ->
                def (id, title, text) = msg
                def tokens = tokenize(text)
                if (tokens)
                    writer << [id, title, tokens]
            }
        }
    }

    String _token = 't:'
    String _content = 'c:'
    def writer = actor {
        int loaded = 0
        def pipe = redis.pipelined()
        loop {
            react(1000) { msg ->
                if (msg == Actor.TIMEOUT) {
                    pipe.sync()
                    println("\nloaded $loaded articles")
                    System.exit(0)
                } else {
                    def (id, title, tokens) = msg
                  //pipe.sadd(_content + id, *tokens)
                    pipe.set(_content + id, title)
                    tokens.each { pipe.sadd(_token + it, id) }
                    if (loaded % 1_000_000 == 0) print(loaded)
                    else if (loaded % 10_000 == 0) print('.')
                    ++loaded
                }
            }
        }
    }

    @CompileStatic
    class Parser extends org.xml.sax.helpers.DefaultHandler {
        int level = 0
        boolean capture = false
        String id, title, str

        @Override
        void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            switch(qName) {
                case 'page': case 'revision': ++level; break
                case 'id': case 'title': capture = level == 1; break
                case 'text': capture = level == 2; break
                default: capture = false; break
            }
        }
        @Override
        void endElement(String uri, String localName, String qName) throws SAXException {
            switch(qName) {
                case 'page': case 'revision': --level; break
                case 'id':     if (capture) id = str; break
                case 'title':  if (capture) title = str; break
                case 'text':   if (capture) document(id, title, str); break // text is always after id and title
            }
            capture = false
        }
        @Override
        void characters(char[] ch, int start, int length) throws SAXException {
            if (capture) str = new String(ch, start, length)
        }
    }

    def splitter = ~/[\s,\.\?!"':=\|\<\>\(\)\[\]\{\}\/\\]+/
    def hostname = ~/[\w-]+(\.[\w-]+)+(?:\/[\w\/\+-]+)?/
    List<String> tokenize(String s) {
        def prev = ''
        // Groovy unique() is O(N^2) :/
        splitter.split(s)
                .grep { it.length() > 2 } .collect { it.toLowerCase() } .grep { !stopwords.contains(it) }
                .sort().inject([]) { uniq, token ->
            if (token != prev) {
                uniq << token
                prev = token
            }
            uniq
        } +
        hostname.matcher(s).collect { it[0] }
    }
}

@Singleton
class Stopwords {
    def lang = ['en', '_special']
    def words = lang.collect {
            this.class.classLoader.getResourceAsStream("stopwords/$it").withStream {
                it.readLines('UTF-8').collect {
                    it.replaceAll('\\s+', '').toLowerCase()
                }
            } .grep { it }
        } .flatten() as Set
}

new Loader('enwiki-20150901-pages-articles.xml').load()
