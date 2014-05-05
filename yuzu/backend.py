from rdflib import *
from rdflib.util import from_n3
import sqlite3
import sys
import getopt

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

        cursor.execute("select fragment, property, object, inverse from triples where subject=?", (id,))
        rows = cursor.fetchall()
        if rows:
            for f, p, o, i in rows:
                if i:
                    g.add(from_n3(o), from_n3(p), self.name(id, f))
                else:
                    g.add((self.name(id, f), from_n3(p), from_n3(o)))
            conn.close()
            return g
        else:
            print("Not found: %s" % id)
            return None

    def load(self):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        cursor.execute("create table if not exists [triples] ([subject] VARCHAR(80), [fragment] VARCHAR(80), property VARCHAR(80) NOT NULL, object VARCHAR(256) NOT NULL, inverse INT DEFAULT 0, query VARCHAR(256) NOT NULL)")
        cursor.execute("CREATE INDEX if not exists k_triples_subject ON [triples] ( subject )")
        cursor.execute("begin transaction")
        for line in sys.stdin:
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
                if obj.startswith("\""):
                    query = obj[1:obj.rindex("\"")]
                elif obj.startswith("<"):
                    query = obj[1:-1]
                else:
                    query = ""
                cursor.execute("insert into triples values (?, ?, ?, ?, 0, ?)", (id, frag, prop, obj, query))
                if obj.startswith("<" + BASE_NAME):
                    if '#' in obj:
                        id = subj[len(BASE_NAME) + 1:subj.index('#')]
                        frag = subj[subj.index('#') + 1:-1]
                    else:
                        id = subj[len(BASE_NAME) + 1:-1]
                        frag = ""
                    cursor.execute("insert into triples values (?, ?, ?, ?, 0, '')", (id, frag, prop, "<"+subj+">"))
        cursor.execute("end transaction")
        conn.close()

    def search(self, prop, query):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        cursor.execute("select subject, query from triples where property=? and query=?", (prop, query))
        rows = cursor.fetchall()
        if rows:
            results = [(s, q) for s, q in rows]
            conn.close()
            return "EXACT", results
        else:
            cursor.execute("select subject, query from triples where property=? and query like ?", (prop, "%%%s%%" % query))
            rows = cursor.fetchall()
            if rows:
                results = [(s, q) for s, q in rows]
                conn.close()
                return "APPROX", results
            else:
                conn.close()
                return "FAIL", []


if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:], 'd:')[0])
    backend = RDFBackend(opts.get('-d', DB_FILE))
    backend.load()




