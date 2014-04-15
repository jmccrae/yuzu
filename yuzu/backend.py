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




