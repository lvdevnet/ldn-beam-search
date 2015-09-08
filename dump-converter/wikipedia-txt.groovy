import groovy.transform.CompileStatic
import org.xml.sax.Attributes
import org.xml.sax.SAXException

class Converter {
    String xml
    int chunk, current = -1, written = -1
    BufferedWriter out
    def stopwords = Stopwords.instance.words

    Converter(String _xml, int _chunk) {
        xml = _xml; chunk = _chunk
    }

    void convert() {
        javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().parse(xml, new Parser())
    }

    @CompileStatic
    void document(String id, String title, String text) {
        if (!makesense(text)) return
        rotate()
        List<String> tokens = tokenize(text)
        if (tokens) {
            out << sanitize(id) << '\n' << sanitize(title) << '\n' << tokens.join(' ') << '\n'
            ++written
            if (written % 1000 == 0) print('.')
        }
    }

    @CompileStatic
    boolean makesense(String text) {
        !text.startsWith('#REDIRECT ') && !text.startsWith('#redirect ')
    }

    @CompileStatic
    void rotate() {
        if (written >= chunk || written == -1) {
            if (out) out.close()
            ++current
            if (current > 9) System.exit(0)
            print(current*chunk + '.')
            out = new File("txt/wiki-${current}.txt").newWriter('UTF-8')
            written = 0
        }
    }

    @CompileStatic
    String sanitize(String text) {
        text.replaceAll(/[\n\r]+/, '')
    }

    def splitter = ~/[\s,\.\?!"':=\|\<\>\(\)\[\]\{\}\/\\]+/
    List<String> tokenize(String s) {
        String prev = ''
        // Groovy unique() is O(N^2) :/
        splitter.split(s)
                .grep { it.length() > 2 } .collect { it.toLowerCase() } .grep { !stopwords.contains(it) }
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

new Converter('enwiki-20150901-pages-articles.xml', 10_000).convert()
