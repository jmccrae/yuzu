from rdflib import *
from rdflib.util import from_n3
import sqlite3
import sys
import getopt
import gzip

from yuzu.settings import *

__author__ = 'jmccrae'


class RDFBackend:
    def __init__(self, db=DB_FILE):
        self.db = db

    @staticmethod
    def name(id, frag):
        if frag:
            return URIRef("%s%s#%s" % (BASE_NAME, id, frag))
        else:
            return URIRef("%s%s" % (BASE_NAME, id))


    def lookup(self, id):
        g = ConjunctiveGraph()
        g.bind("lemon", "http://lemon-model.net/lemon#")
        g.bind("owl", str(OWL))
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        cursor.execute("select fragment, property, object from triples where subject=?", (id,))
        rows = cursor.fetchall()
        if rows:
            for f, p, o in rows:
                g.add((self.name(id, f), from_n3(p), from_n3(o)))
            conn.close()
            return g
        else:
            print("Not found: %s" % id)
            return None

    def search(self, value, prop, limit=20):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        if prop:
            cursor.execute("select subject from triples where property=? and object like ? limit ?", ("<%s>" % prop, "%%%s%%" % value, limit))
        else:
            cursor.execute("select subject from triples where object like ? limit ?", ("%%%s%%" % value, limit))
        rows = cursor.fetchall()
        return [uri for uri, in rows]

    def list_resources(self, offset, limit):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        cursor.execute("select distinct count(subject) from triples")
        n, = cursor.fetchone()
        if offset >= int(n):
            return False, None
        cursor.execute("select distinct subject from triples offset limit ? offset ?", (limit, offset))
        refs = [uri for uri, in cursor.fetchall()]
        return int(n) > limit + offset, refs
        

    def load(self, input_stream):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        cursor.execute("create table if not exists [triples] ([subject] VARCHAR(80), [fragment] VARCHAR(80), property VARCHAR(80) NOT NULL, object VARCHAR(256) NOT NULL)")
        cursor.execute("create index if not exists k_triples_subject ON [triples] ( subject )")
        for line in input_stream:
            e = line.split(" ")
            subj = e[0][1:-1]
            if subj.startswith(BASE_NAME):
                if '#' in subj:
                    id = subj[len(BASE_NAME):subj.index('#')]
                    frag = subj[subj.index('#') + 1:]
                else:
                    id = subj[len(BASE_NAME):]
                    frag = ""
                if id.endswith(".rdf") or id.endswith(".ttl") or id.endswith(".nt") or id.endswith(".json") or id.endswith(".xml"):
                    sys.stderr.write("File type at end of name (%s) dropped\n" % id)
                    id = id[:id.rindex(".")]
                prop = e[1]
                obj = " ".join(e[2:-1])
                cursor.execute("insert into triples values (?, ?, ?, ?)", (id, frag, prop, obj))
        conn.commit()
        cursor.close()
        conn.close()

if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:],'d:f:')[0])
    backend = RDFBackend(opts.get('-d', DB_FILE))
    input_file = opts.get('-f', DUMP_FILE)
    if input_file.endswith(".gz"):
        input_stream = gzip.open(input_file)
    else:
        input_stream = open(input_file)
    backend.load(input_stream)




