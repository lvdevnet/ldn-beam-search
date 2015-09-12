import java.util.concurrent.atomic.AtomicInteger
import groovy.transform.CompileStatic
import groovyx.gpars.actor.Actor
import static groovyx.gpars.actor.Actors.actor
import org.xml.sax.Attributes
import org.xml.sax.SAXException

class Converter {
    String xml
    int chunk, nfiles, total, current = 0, written = 0
    List<Actor> writers
    AtomicInteger inflight = new AtomicInteger(0)
    def stopwords = Stopwords.instance.words

    Converter(String _xml, int _chunk, int _nfiles) {
        xml = _xml; chunk = _chunk; nfiles = _nfiles
        total = nfiles*chunk
    }

    void convert() {
        writers = (0..nfiles-1).collect { n ->
            BufferedWriter out = new File("txt/wiki-${n}.txt").newWriter('UTF-8')
            actor {
                loop {
                    react { msg ->
                        if (msg == 'exit') {
                            out.close()
                            terminate()
                        } else {
                            inflight.decrementAndGet()
                            def (id, title, text) = msg
                            List<String> tokens = tokenize(text)
                            if (tokens)
                                out << sanitize(id) << '\n' << sanitize(title) << '\n' << tokens.join(' ') << '\n'
                        }
                    }
                }
            }
        }
        javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().parse(xml, new Parser())
    }

    @CompileStatic
    void document(String id, String title, String text) {
        if (!makesense(text)) return
        writers[current++ % nfiles] << [id, title, text]
        inflight.incrementAndGet()
        if (written++ % 10_000 == 0) print(written-1)
        else if (written % 1_000 == 0) print('.')
        if (written >= total) {
            while (inflight.get() > 0) {
                print((total-inflight.get()) + '.')
                Thread.sleep(1000)
            }
            writers.each { it << 'exit'; it.join() }
            println(total)
            System.exit(0)
        }
        while (inflight.get() > 1000)
            Thread.sleep(100)
    }

    @CompileStatic
    boolean makesense(String text) {
        !text.startsWith('#REDIRECT ') && !text.startsWith('#redirect ')
    }

    @CompileStatic
    String sanitize(String text) {
        text.replaceAll(/[\n\r]+/, '')
    }

    def splitter = ~/[\s,\.\?!"':=\|\<\>\(\)\[\]\{\}\/\\_-]+/
    List<String> tokenize(String s) {
        String prev = ''
        splitter.split(s)
                .grep { it.length() > 2 && it.charAt(0).isLetter() }
                .collect { it.toLowerCase() }
                .grep { !stopwords.contains(it) }
                // Groovy unique() is O(N^2) :/
                .sort().inject([]) { uniq, token ->
                                        if (token != prev) {
                                            uniq << token
                                            prev = token
                                        }
                                        uniq
        }
    }

    @CompileStatic
    class Parser extends org.xml.sax.helpers.DefaultHandler {
        int level = 0
        boolean capture = false
        String id, title
        StringBuilder str

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
                case 'id':     if (capture)    id = str.toString(); str = null; break
                case 'title':  if (capture) title = str.toString(); str = null; break
                case 'text':   if (capture) document(id, title, str.toString()); str = null; break // text is always after id and title
            }
            capture = false
        }
        @Override
        void characters(char[] ch, int start, int length) throws SAXException {
            if (capture) {
                if (!str) str = new StringBuilder()
                str.append(ch, start, length)
            }
        }
    }
}

@Singleton
class Stopwords {
    def lang = ['en', '_special']
    def words = lang.collect {
            new File("stopwords/$it").withReader('UTF-8') {
                it.readLines().collect {
                    it.replaceAll('\\s+', '').toLowerCase()
                }
            } .grep { it }
        } .flatten() as Set
}

new Converter('enwiki-20150901-pages-articles.xml', 10_000, 10).convert()
