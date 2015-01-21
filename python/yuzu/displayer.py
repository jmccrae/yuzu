from rdflib.term import Literal, URIRef
from rdflib.namespace import RDF, RDFS, XSD, OWL, DC, DCTERMS
import re
from yuzu.settings import (PREFIX1_QN, PREFIX1_URI, DATAID,
                           PREFIX2_QN, PREFIX2_URI, DCAT,
                           PREFIX3_QN, PREFIX3_URI, FOAF,
                           PREFIX4_QN, PREFIX4_URI, ODRL,
                           PREFIX5_QN, PREFIX5_URI, PROV,
                           PREFIX6_QN, PREFIX6_URI, VOID,
                           PREFIX7_QN, PREFIX7_URI,
                           PREFIX8_QN, PREFIX8_URI,
                           PREFIX9_QN, PREFIX9_URI, PROP_NAMES, BASE_NAME)


# Displayers are here due to circular importing :(
class DefaultDisplayer:
    def uri_to_str(self, uri):
        if uri in PROP_NAMES:
            return PROP_NAMES[uri]
        elif uri.startswith(PREFIX1_URI):
            return "%s:%s" % (PREFIX1_QN, uri[len(PREFIX1_URI):])
        elif uri.startswith(PREFIX2_URI):
            return "%s:%s" % (PREFIX2_QN, uri[len(PREFIX2_URI):])
        elif uri.startswith(PREFIX3_URI):
            return "%s:%s" % (PREFIX3_QN, uri[len(PREFIX3_URI):])
        elif uri.startswith(PREFIX4_URI):
            return "%s:%s" % (PREFIX4_QN, uri[len(PREFIX4_URI):])
        elif uri.startswith(PREFIX5_URI):
            return "%s:%s" % (PREFIX5_QN, uri[len(PREFIX5_URI):])
        elif uri.startswith(PREFIX6_URI):
            return "%s:%s" % (PREFIX6_QN, uri[len(PREFIX6_URI):])
        elif uri.startswith(PREFIX7_URI):
            return "%s:%s" % (PREFIX7_QN, uri[len(PREFIX7_URI):])
        elif uri.startswith(PREFIX8_URI):
            return "%s:%s" % (PREFIX8_QN, uri[len(PREFIX8_URI):])
        elif uri.startswith(PREFIX9_URI):
            return "%s:%s" % (PREFIX9_QN, uri[len(PREFIX9_URI):])
        elif uri.startswith(BASE_NAME):
            return "%s" % uri[len(BASE_NAME):]
        elif uri.startswith(str(RDF)):
            return uri[len(str(RDF)):]
        elif uri.startswith(str(RDFS)):
            return uri[len(str(RDFS)):]
        elif uri.startswith(str(OWL)):
            return uri[len(str(OWL)):]
        elif uri.startswith(str(DC)):
            return uri[len(str(DC)):]
        elif uri.startswith(str(DCTERMS)):
            return uri[len(str(DCTERMS)):]
        elif uri.startswith(str(XSD)):
            return uri[len(str(XSD)):]
        elif uri.startswith(DATAID):
            return "dataid:" + uri[len(str(DATAID)):]
        elif uri.startswith(DCAT):
            return "dcat:" + uri[len(str(DCAT)):]
        elif uri.startswith(FOAF):
            return "foaf:" + uri[len(str(FOAF)):]
        elif uri.startswith(ODRL):
            return "odrl:" + uri[len(str(ODRL)):]
        elif uri.startswith(PROV):
            return "prov:" + uri[len(str(PROV)):]
        elif uri.startswith(VOID):
            return "void:" + uri[len(str(VOID)):]
        else:
            return uri

    def apply(self, node):
        if type(node) == URIRef:
            u = self.uri_to_str(str(node))
            if u:
                return u
            else:
                return str(node)
        elif type(node) == Literal:
            return str(node)
        else:
            return ""


class PrettyDisplayer:
    @staticmethod
    def magic_string(text):
        if text:
            s = re.sub("([a-z])([A-Z])", "\\1 \\2", text)
            s = re.sub("_", " ", s)
            return s[0].upper() + s[1:]
        else:
            ""

    def uri_to_str(self, uri):
        if uri in PROP_NAMES:
            return PROP_NAMES[uri]
        elif uri.startswith(PREFIX1_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX1_URI):]))
        elif uri.startswith(PREFIX2_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX2_URI):]))
        elif uri.startswith(PREFIX3_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX3_URI):]))
        elif uri.startswith(PREFIX4_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX4_URI):]))
        elif uri.startswith(PREFIX5_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX5_URI):]))
        elif uri.startswith(PREFIX6_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX6_URI):]))
        elif uri.startswith(PREFIX7_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX7_URI):]))
        elif uri.startswith(PREFIX8_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX8_URI):]))
        elif uri.startswith(PREFIX9_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX9_URI):]))
        elif uri.startswith(BASE_NAME):
            return self.magic_string("%s" % uri[len(BASE_NAME):])
        elif uri.startswith(str(RDF)):
            return self.magic_string(uri[len(str(RDF)):])
        elif uri.startswith(str(RDFS)):
            return self.magic_string(uri[len(str(RDFS)):])
        elif uri.startswith(str(OWL)):
            return self.magic_string(uri[len(str(OWL)):])
        elif uri.startswith(str(DC)):
            return self.magic_string(uri[len(str(DC)):])
        elif uri.startswith(str(DCTERMS)):
            return self.magic_string(uri[len(str(DCTERMS)):])
        elif uri.startswith(str(XSD)):
            return self.magic_string(uri[len(str(XSD)):])
        elif uri.startswith(DATAID):
            return self.magic_string(uri[len(str(DATAID)):])
        elif uri.startswith(DCAT):
            return self.magic_string(uri[len(str(DCAT)):])
        elif uri.startswith(FOAF):
            return self.magic_string(uri[len(str(FOAF)):])
        elif uri.startswith(ODRL):
            return self.magic_string(uri[len(str(ODRL)):])
        elif uri.startswith(PROV):
            return self.magic_string(uri[len(str(PROV)):])
        elif uri.startswith(VOID):
            return self.magic_string(uri[len(str(VOID)):])
        else:
            return uri

    def apply(self, node):
        if type(node) == URIRef:
            u = self.uri_to_str(str(node))
            if u:
                return u
            else:
                return str(node)
        elif type(node) == Literal:
            return str(node)
        if type(node) == str:
            return self.uri_to_str(node)
        else:
            return ""

# Displayer to show URIs
DISPLAYER = PrettyDisplayer()
