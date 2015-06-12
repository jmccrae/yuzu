function find_ref_context(obj) {
    if(typeof(obj) === "object") {
        if("@graph" in obj) {
            return find_ref_context(obj["@graph"]);
        } else if ("@id" in obj && obj["@id"].match(/^[^#]*$/)) {
            return obj;
        } else {
            for(key in obj) {
                var r = find_ref_context(obj[key]);
                if(r) {
                    return r;
                }
            }
        }
    }
    return null;
}

var NIF = "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#";

function build_legend(jsonld) {
    vals = {};
    context = jsonld["@context"]
    for(key in context) {
        var ctxVal;
        if(typeof(context[key]) === "object") {
            ctxVal = context[key]["@id"];
        } else {
            ctxVal = context[key];
        }

        if(key[0] != "@" &&
           ctxVal.substr(0, NIF.length) != NIF &&
           ctxVal[ctxVal.length - 1] != '#' &&
           ctxVal[ctxVal.length - 1] != '/') {
           vals[key] = {};
        }
    }
    for(k2 in jsonld["@graph"]) {
        var elem = jsonld["@graph"][k2];
        for(v in vals) {
            if(v in elem) {
                var v2;
                if(typeof(elem[v]) === "object") {
                    v2 = elem[v]["@value"];
                } else {
                    v2 = elem[v];
                }
                if(v2) {
                    vals[v][v.replace(/\W/g, '') + "__" + v2.replace(/\W/g, '')] = v + " - " + v2;
                }
            }
        }
        if("@reverse" in elem &&
            "referenceContext" in elem["@reverse"]) {
            for(k3 in elem["@reverse"]["referenceContext"]) {
                var elem2 = elem["@reverse"]["referenceContext"][k3];
                for(v in vals) {
                    if(v in elem2) {
                        var v2;
                        if(typeof(elem2[v]) === "object") {
                            v2 = elem2[v]["@value"];
                        } else {
                            v2 = elem2[v];
                        }
                        if(v2) {
                            vals[v][v.replace(/\W/g, '') + "__" + v2.replace(/\W/g, '')] = v + " - " + v2;
                        }
                    }
                }
            }
        }
    }

    return vals;
}

function find_annotations(obj, n) {
    var annos = [];
    for(var i = 0; i < n; i++) {
        annos.push([]);
    }
    for(i in obj["@graph"]) {
        var elem = obj["@graph"][i];
        if("beginindex" in elem) {
            var begin = parseInt(elem["beginindex"]["@value"]);
            var end = parseInt(elem["endindex"]["@value"]);
            for(var j = begin; j < Math.min(end, n); j++) {
                annos[j].push(elem);
            }
        }
        if("@reverse" in elem &&
            "referenceContext" in elem["@reverse"]) {
            for(k3 in elem["@reverse"]["referenceContext"]) {
                var elem2 = elem["@reverse"]["referenceContext"][k3];
                if("beginIndex" in elem2) {
                    var begin = parseInt(elem2["beginIndex"]["@value"]);
                    var end = parseInt(elem2["endIndex"]["@value"]);
                    for(var j = begin; j < Math.min(end, n); j++) {
                        annos[j].push(elem2);
                    }
                }
            }
        }
    }
    return annos;
}

function anno_to_str(anno, legend) {
    var str = "";
    for(k in anno) {
        if(k in legend) {
            var val;
            if(typeof(anno[k]) === "object") {
                val = anno[k]["@value"];
            } else {
                val = anno[k];
            }
            str += k + " - " + val + "<br/>";
        }
    }
    return str;
}

function anno_classes(anno, legend) {
    var classes = [];
    for(k in anno) {
        if(k in legend) {
            var val;
            if(typeof(anno[k]) === "object") {
                val = anno[k]["@value"];
            } else {
                val = anno[k];
            }
            if(val) {
                classes.push(k.replace(/\W/g, '') + "__" + val.replace(/\W/g, ''))
            }
        }
    }
    return classes;
}

function rand_color() {
    var pos = Math.random() * 5;
    var r = Math.floor(Math.sin(pos + 0) * 127 + 128);
    var g = Math.floor(Math.sin(pos + 1) * 127 + 128);
    var b = Math.floor(Math.sin(pos + 3) * 127 + 128);
    return "rgb("+r+", "+g+", "+b+")";
}

var active_annos = [];

function toggle_color(c) {
    if(active_annos.indexOf(c) >= 0) {
        $("." + c).css("color", "black");
        $(".lk_" + c).css("color", "black");
        delete active_annos[active_annos.indexOf(c)];
    } else {
        var col = rand_color();
        $("." + c).css("color", col);
        $(".lk_" + c).css("color", col);
        active_annos.push(c);
    }
}

