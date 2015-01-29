package com.github.jmccrae.yuzu

object YuzuSettings {
  // This file contains all relevant configuration for the system

  // The location where this server is to be deployed to
  // Only URIs in the dump that start with this address will be published
  // Should end with a trailing /
  val BASE_NAME = "http://linghub.lider-project.eu/"
  // The prefix that this servlet will be deployed, e.g. 
  // if the servlet is at http://www.example.org/yuzu/ the context 
  // is /yuzu
  val CONTEXT = ""
  // The data download will be at BASE_NAME + DUMP_URI
  val DUMP_URI = "/linghub.nt.gz"
  // The local path to the data
  val DUMP_FILE = "../linghub.nt.gz"
  // Where the database should appear
  val DB_FILE = "linghub.db"
  // The name of the server
  val DISPLAY_NAME = "LingHub"

    // The extra namespaces to be abbreviated in HTML and RDF/XML documents if desired
  val PREFIX1_URI = "http://www.w3.org/ns/dcat#"
  val PREFIX1_QN = "dcat"
  val PREFIX2_URI = "http://xmlns.com/foaf/0.1/"
  val PREFIX2_QN = "foaf"
  val PREFIX3_URI = "http://www.clarin.eu/cmd/"
  val PREFIX3_QN = "cmd"
  val PREFIX4_URI = "http://purl.org/dc/terms/"
  val PREFIX4_QN = "dct"
  val PREFIX5_URI = "http://www.resourcebook.eu/lremap/owl/lremap_resource.owl#"
  val PREFIX5_QN = "lremap"
  val PREFIX6_URI = "http://purl.org/ms-lod/MetaShare.ttl#"
  val PREFIX6_QN = "metashare"
  val PREFIX7_URI = "http://purl.org/ms-lod/BioServices.ttl"
  val PREFIX7_QN = "bio"
  val PREFIX8_URI = "http://www.lexvo.org/id/iso639-3/"
  val PREFIX8_QN = "iso639"
  val PREFIX9_URI = "http://www.example.com#"
  val PREFIX9_QN = "ex9"

  // Used for DATAID
  val DCAT = "http://www.w3.org/ns/dcat#"
  val VOID = "http://rdfs.org/ns/void#"
  val DATAID = "http://dataid.dbpedia.org/ns#"
  val FOAF = "http://xmlns.com/foaf/0.1/"
  val ODRL = "http://www.w3.org/ns/odrl/2/"
  val PROV = "http://www.w3.org/ns/prov#"

  // The maximum number of results to return from a YuzuQL query (or -1 for no
  // limit)
  val YUZUQL_LIMIT = 1000
  // If using an external SPARQL endpoint, the address of this
  // or None if you wish to use only YuzuQL
  val SPARQL_ENDPOINT : Option[String] = None
  // Path to the license (set to null to disable)
  val LICENSE_PATH = "/license.html"
  // Path to the search (set to null to disable)
  val SEARCH_PATH = "/search"
  // Path to static assets
  val ASSETS_PATH = "/assets/"
  // Path to SPARQL (set to null to disable)
  val SPARQL_PATH = "/sparql"
  // Path to site contents list (set to null to disable)
  val LIST_PATH = "/list"
  // Path to Data ID (metadata) (no initial slash)
  val METADATA_PATH = "about"

  // Properties to use as facets
  val FACETS = Seq(
    Map("uri" -> "http://purl.org/dc/elements/1.1/title", "label" -> "Title", "list" -> false),
    Map("uri" -> "http://purl.org/dc/terms/language", "label" -> "Language"),
    Map("uri" -> "http://purl.org/dc/elements/1.1/rights", "label" -> "Rights"),
    Map("uri" -> "http://purl.org/dc/terms/type", "label" -> "Type"),
    //Map("uri" -> "http://purl.org/dc/elements/1.1/issued", "label" -> "Date Issued"),
    Map("uri" -> "http://purl.org/dc/elements/1.1/creator", "label" -> "Creator"),
    Map("uri" -> "http://purl.org/dc/elements/1.1/source", "label" -> "Source"),
    Map("uri" -> "http://purl.org/dc/elements/1.1/description", "label" -> "Description", "list" -> false),
    //Map("uri" -> "http://www.w3.org/ns/dcat#downloadURL", "label" -> "Download URL"),
    Map("uri" -> "http://www.w3.org/ns/dcat#accessURL", "label" -> "Access URL", "list" -> false),
    Map("uri" -> "http://www.w3.org/ns/dcat#contactPoint", "label" -> "Contact Point", "list" -> false)
  )
  // Properties to use as labels
  val LABELS = Set(
    "<http://www.w3.org/2000/01/rdf-schema#label>",
    "<http://xmlns.com/foaf/0.1/nick>",
    "<http://purl.org/dc/elements/1.1/title>",
    "<http://purl.org/rss/1.0/title>",
    "<http://xmlns.com/foaf/0.1/name>"
  )
  // The displayer for URIs
  val DISPLAYER = PrettyDisplayer
  
  // Any forced names on properties
  val PROP_NAMES = Map(
  "http://www.lexvo.org/id/iso639-3/abk" -> "Abkhaz",
"http://www.lexvo.org/id/iso639-3/aar" -> "Afar",
"http://www.lexvo.org/id/iso639-3/afr" -> "Afrikaans",
"http://www.lexvo.org/id/iso639-3/aka" -> "Akan",
"http://www.lexvo.org/id/iso639-3/sqi" -> "Albanian",
"http://www.lexvo.org/id/iso639-3/amh" -> "Amharic",
"http://www.lexvo.org/id/iso639-3/ara" -> "Arabic",
"http://www.lexvo.org/id/iso639-3/arg" -> "Aragonese",
"http://www.lexvo.org/id/iso639-3/hye" -> "Armenian",
"http://www.lexvo.org/id/iso639-3/asm" -> "Assamese",
"http://www.lexvo.org/id/iso639-3/ava" -> "Avaric",
"http://www.lexvo.org/id/iso639-3/ave" -> "Avestan",
"http://www.lexvo.org/id/iso639-3/aym" -> "Aymara",
"http://www.lexvo.org/id/iso639-3/aze" -> "Azerbaijani",
"http://www.lexvo.org/id/iso639-3/bam" -> "Bambara",
"http://www.lexvo.org/id/iso639-3/bak" -> "Bashkir",
"http://www.lexvo.org/id/iso639-3/eus" -> "Basque",
"http://www.lexvo.org/id/iso639-3/bel" -> "Belarusian",
"http://www.lexvo.org/id/iso639-3/ben" -> "Bengali, Bangla",
"http://www.lexvo.org/id/iso639-3/bih" -> "Bihari",
"http://www.lexvo.org/id/iso639-3/bis" -> "Bislama",
"http://www.lexvo.org/id/iso639-3/bos" -> "Bosnian",
"http://www.lexvo.org/id/iso639-3/bre" -> "Breton",
"http://www.lexvo.org/id/iso639-3/bul" -> "Bulgarian",
"http://www.lexvo.org/id/iso639-3/mya" -> "Burmese",
"http://www.lexvo.org/id/iso639-3/cat" -> "Catalan",
"http://www.lexvo.org/id/iso639-3/cha" -> "Chamorro",
"http://www.lexvo.org/id/iso639-3/che" -> "Chechen",
"http://www.lexvo.org/id/iso639-3/nya" -> "Chichewa, Chewa, Nyanja",
"http://www.lexvo.org/id/iso639-3/zho" -> "Chinese",
"http://www.lexvo.org/id/iso639-3/chv" -> "Chuvash",
"http://www.lexvo.org/id/iso639-3/cor" -> "Cornish",
"http://www.lexvo.org/id/iso639-3/cos" -> "Corsican",
"http://www.lexvo.org/id/iso639-3/cre" -> "Cree",
"http://www.lexvo.org/id/iso639-3/hrv" -> "Croatian",
"http://www.lexvo.org/id/iso639-3/ces" -> "Czech",
"http://www.lexvo.org/id/iso639-3/dan" -> "Danish",
"http://www.lexvo.org/id/iso639-3/div" -> "Divehi, Dhivehi, Maldivian",
"http://www.lexvo.org/id/iso639-3/nld" -> "Dutch",
"http://www.lexvo.org/id/iso639-3/dzo" -> "Dzongkha",
"http://www.lexvo.org/id/iso639-3/eng" -> "English",
"http://www.lexvo.org/id/iso639-3/epo" -> "Esperanto",
"http://www.lexvo.org/id/iso639-3/est" -> "Estonian",
"http://www.lexvo.org/id/iso639-3/ewe" -> "Ewe",
"http://www.lexvo.org/id/iso639-3/fao" -> "Faroese",
"http://www.lexvo.org/id/iso639-3/fij" -> "Fijian",
"http://www.lexvo.org/id/iso639-3/fin" -> "Finnish",
"http://www.lexvo.org/id/iso639-3/fra" -> "French",
"http://www.lexvo.org/id/iso639-3/ful" -> "Fula, Fulah, Pulaar, Pular",
"http://www.lexvo.org/id/iso639-3/glg" -> "Galician",
"http://www.lexvo.org/id/iso639-3/kat" -> "Georgian",
"http://www.lexvo.org/id/iso639-3/deu" -> "German",
"http://www.lexvo.org/id/iso639-3/ell" -> "Greek (modern)",
"http://www.lexvo.org/id/iso639-3/grn" -> "Guaraní",
"http://www.lexvo.org/id/iso639-3/guj" -> "Gujarati",
"http://www.lexvo.org/id/iso639-3/hat" -> "Haitian, Haitian Creole",
"http://www.lexvo.org/id/iso639-3/hau" -> "Hausa",
"http://www.lexvo.org/id/iso639-3/heb" -> "Hebrew (modern)",
"http://www.lexvo.org/id/iso639-3/her" -> "Herero",
"http://www.lexvo.org/id/iso639-3/hin" -> "Hindi",
"http://www.lexvo.org/id/iso639-3/hmo" -> "Hiri Motu",
"http://www.lexvo.org/id/iso639-3/hun" -> "Hungarian",
"http://www.lexvo.org/id/iso639-3/ina" -> "Interlingua",
"http://www.lexvo.org/id/iso639-3/ind" -> "Indonesian",
"http://www.lexvo.org/id/iso639-3/ile" -> "Interlingue",
"http://www.lexvo.org/id/iso639-3/gle" -> "Irish",
"http://www.lexvo.org/id/iso639-3/ibo" -> "Igbo",
"http://www.lexvo.org/id/iso639-3/ipk" -> "Inupiaq",
"http://www.lexvo.org/id/iso639-3/ido" -> "Ido",
"http://www.lexvo.org/id/iso639-3/isl" -> "Icelandic",
"http://www.lexvo.org/id/iso639-3/ita" -> "Italian",
"http://www.lexvo.org/id/iso639-3/iku" -> "Inuktitut",
"http://www.lexvo.org/id/iso639-3/jpn" -> "Japanese",
"http://www.lexvo.org/id/iso639-3/jav" -> "Javanese",
"http://www.lexvo.org/id/iso639-3/kal" -> "Kalaallisut, Greenlandic",
"http://www.lexvo.org/id/iso639-3/kan" -> "Kannada",
"http://www.lexvo.org/id/iso639-3/kau" -> "Kanuri",
"http://www.lexvo.org/id/iso639-3/kas" -> "Kashmiri",
"http://www.lexvo.org/id/iso639-3/kaz" -> "Kazakh",
"http://www.lexvo.org/id/iso639-3/khm" -> "Khmer",
"http://www.lexvo.org/id/iso639-3/kik" -> "Kikuyu, Gikuyu",
"http://www.lexvo.org/id/iso639-3/kin" -> "Kinyarwanda",
"http://www.lexvo.org/id/iso639-3/kir" -> "Kyrgyz",
"http://www.lexvo.org/id/iso639-3/kom" -> "Komi",
"http://www.lexvo.org/id/iso639-3/kon" -> "Kongo",
"http://www.lexvo.org/id/iso639-3/kor" -> "Korean",
"http://www.lexvo.org/id/iso639-3/kur" -> "Kurdish",
"http://www.lexvo.org/id/iso639-3/kua" -> "Kwanyama, Kuanyama",
"http://www.lexvo.org/id/iso639-3/lat" -> "Latin",
"http://www.lexvo.org/id/iso639-3/ltz" -> "Luxembourgish, Letzeburgesch",
"http://www.lexvo.org/id/iso639-3/lug" -> "Ganda",
"http://www.lexvo.org/id/iso639-3/lim" -> "Limburgish, Limburgan, Limburger",
"http://www.lexvo.org/id/iso639-3/lin" -> "Lingala",
"http://www.lexvo.org/id/iso639-3/lao" -> "Lao",
"http://www.lexvo.org/id/iso639-3/lit" -> "Lithuanian",
"http://www.lexvo.org/id/iso639-3/lub" -> "Luba-Katanga",
"http://www.lexvo.org/id/iso639-3/lav" -> "Latvian",
"http://www.lexvo.org/id/iso639-3/glv" -> "Manx",
"http://www.lexvo.org/id/iso639-3/mkd" -> "Macedonian",
"http://www.lexvo.org/id/iso639-3/mlg" -> "Malagasy",
"http://www.lexvo.org/id/iso639-3/msa" -> "Malay",
"http://www.lexvo.org/id/iso639-3/mal" -> "Malayalam",
"http://www.lexvo.org/id/iso639-3/mlt" -> "Maltese",
"http://www.lexvo.org/id/iso639-3/mri" -> "Māori",
"http://www.lexvo.org/id/iso639-3/mar" -> "Marathi (Marāṭhī)",
"http://www.lexvo.org/id/iso639-3/mah" -> "Marshallese",
"http://www.lexvo.org/id/iso639-3/mon" -> "Mongolian",
"http://www.lexvo.org/id/iso639-3/nau" -> "Nauru",
"http://www.lexvo.org/id/iso639-3/nav" -> "Navajo, Navaho",
"http://www.lexvo.org/id/iso639-3/nde" -> "Northern Ndebele",
"http://www.lexvo.org/id/iso639-3/nep" -> "Nepali",
"http://www.lexvo.org/id/iso639-3/ndo" -> "Ndonga",
"http://www.lexvo.org/id/iso639-3/nob" -> "Norwegian Bokmål",
"http://www.lexvo.org/id/iso639-3/nno" -> "Norwegian Nynorsk",
"http://www.lexvo.org/id/iso639-3/nor" -> "Norwegian",
"http://www.lexvo.org/id/iso639-3/iii" -> "Nuosu",
"http://www.lexvo.org/id/iso639-3/nbl" -> "Southern Ndebele",
"http://www.lexvo.org/id/iso639-3/oci" -> "Occitan",
"http://www.lexvo.org/id/iso639-3/oji" -> "Ojibwe, Ojibwa",
"http://www.lexvo.org/id/iso639-3/chu" -> "Old Church Slavonic, Church Slavonic, Old Bulgarian",
"http://www.lexvo.org/id/iso639-3/orm" -> "Oromo",
"http://www.lexvo.org/id/iso639-3/ori" -> "Oriya",
"http://www.lexvo.org/id/iso639-3/oss" -> "Ossetian, Ossetic",
"http://www.lexvo.org/id/iso639-3/pan" -> "Panjabi, Punjabi",
"http://www.lexvo.org/id/iso639-3/pli" -> "Pāli",
"http://www.lexvo.org/id/iso639-3/fas" -> "Persian (Farsi)",
"http://www.lexvo.org/id/iso639-3/pol" -> "Polish",
"http://www.lexvo.org/id/iso639-3/pus" -> "Pashto, Pushto",
"http://www.lexvo.org/id/iso639-3/por" -> "Portuguese",
"http://www.lexvo.org/id/iso639-3/que" -> "Quechua",
"http://www.lexvo.org/id/iso639-3/roh" -> "Romansh",
"http://www.lexvo.org/id/iso639-3/run" -> "Kirundi",
"http://www.lexvo.org/id/iso639-3/ron" -> "Romanian",
"http://www.lexvo.org/id/iso639-3/rus" -> "Russian",
"http://www.lexvo.org/id/iso639-3/san" -> "Sanskrit (Saṁskṛta)",
"http://www.lexvo.org/id/iso639-3/srd" -> "Sardinian",
"http://www.lexvo.org/id/iso639-3/snd" -> "Sindhi",
"http://www.lexvo.org/id/iso639-3/sme" -> "Northern Sami",
"http://www.lexvo.org/id/iso639-3/smo" -> "Samoan",
"http://www.lexvo.org/id/iso639-3/sag" -> "Sango",
"http://www.lexvo.org/id/iso639-3/skr" -> "Saraiki,Seraiki,Siraiki",
"http://www.lexvo.org/id/iso639-3/srp" -> "Serbian",
"http://www.lexvo.org/id/iso639-3/gla" -> "Scottish Gaelic, Gaelic",
"http://www.lexvo.org/id/iso639-3/sna" -> "Shona",
"http://www.lexvo.org/id/iso639-3/sin" -> "Sinhala, Sinhalese",
"http://www.lexvo.org/id/iso639-3/slk" -> "Slovak",
"http://www.lexvo.org/id/iso639-3/slv" -> "Slovene",
"http://www.lexvo.org/id/iso639-3/som" -> "Somali",
"http://www.lexvo.org/id/iso639-3/sot" -> "Southern Sotho",
"http://www.lexvo.org/id/iso639-3/spa" -> "Spanish",
"http://www.lexvo.org/id/iso639-3/sun" -> "Sundanese",
"http://www.lexvo.org/id/iso639-3/swa" -> "Swahili",
"http://www.lexvo.org/id/iso639-3/ssw" -> "Swati",
"http://www.lexvo.org/id/iso639-3/swe" -> "Swedish",
"http://www.lexvo.org/id/iso639-3/tam" -> "Tamil",
"http://www.lexvo.org/id/iso639-3/tel" -> "Telugu",
"http://www.lexvo.org/id/iso639-3/tgk" -> "Tajik",
"http://www.lexvo.org/id/iso639-3/tha" -> "Thai",
"http://www.lexvo.org/id/iso639-3/tir" -> "Tigrinya",
"http://www.lexvo.org/id/iso639-3/bod" -> "Tibetan Standard, Tibetan, Central",
"http://www.lexvo.org/id/iso639-3/tuk" -> "Turkmen",
"http://www.lexvo.org/id/iso639-3/tgl" -> "Tagalog",
"http://www.lexvo.org/id/iso639-3/tsn" -> "Tswana",
"http://www.lexvo.org/id/iso639-3/ton" -> "Tonga (Tonga Islands)",
"http://www.lexvo.org/id/iso639-3/tur" -> "Turkish",
"http://www.lexvo.org/id/iso639-3/tso" -> "Tsonga",
"http://www.lexvo.org/id/iso639-3/tat" -> "Tatar",
"http://www.lexvo.org/id/iso639-3/twi" -> "Twi",
"http://www.lexvo.org/id/iso639-3/tah" -> "Tahitian",
"http://www.lexvo.org/id/iso639-3/uig" -> "Uyghur",
"http://www.lexvo.org/id/iso639-3/ukr" -> "Ukrainian",
"http://www.lexvo.org/id/iso639-3/urd" -> "Urdu",
"http://www.lexvo.org/id/iso639-3/uzb" -> "Uzbek",
"http://www.lexvo.org/id/iso639-3/ven" -> "Venda",
"http://www.lexvo.org/id/iso639-3/vie" -> "Vietnamese",
"http://www.lexvo.org/id/iso639-3/vol" -> "Volapük",
"http://www.lexvo.org/id/iso639-3/wln" -> "Walloon",
"http://www.lexvo.org/id/iso639-3/cym" -> "Welsh",
"http://www.lexvo.org/id/iso639-3/wol" -> "Wolof",
"http://www.lexvo.org/id/iso639-3/fry" -> "Western Frisian",
"http://www.lexvo.org/id/iso639-3/xho" -> "Xhosa",
"http://www.lexvo.org/id/iso639-3/yid" -> "Yiddish",
"http://www.lexvo.org/id/iso639-3/yor" -> "Yoruba",
"http://www.lexvo.org/id/iso639-3/zha" -> "yor Zhuang, Chuang",
"http://www.lexvo.org/id/iso639-3/zul" -> "Zulu",
"http://babelnet.org/rdf/s00000657n" -> "Word sense",
"http://babelnet.org/rdf/s00004328n" -> "Normalization",
"http://babelnet.org/rdf/s00004349n" -> "Annotation",
"http://babelnet.org/rdf/s00004349n" -> "Annotations",
"http://babelnet.org/rdf/s00004905n" -> "Apis",
"http://babelnet.org/rdf/s00007291n" -> "Authoring tool",
"http://babelnet.org/rdf/s00007783n" -> "Background knowledge",
"http://babelnet.org/rdf/s00009853n" -> "Workbench",
"http://babelnet.org/rdf/s00010387n" -> "Bilingual lexicons",
"http://babelnet.org/rdf/s00019680n" -> "Segmentation",
"http://babelnet.org/rdf/s00020948n" -> "Query language",
"http://babelnet.org/rdf/s00021343n" -> "Compiler",
"http://babelnet.org/rdf/s00022637n" -> "Coreference",
"http://babelnet.org/rdf/s00022825n" -> "Corpora",
"http://babelnet.org/rdf/s00022825n" -> "Corpus",
"http://babelnet.org/rdf/s00022826n" -> "Corpus",
"http://babelnet.org/rdf/s00022827n" -> "Corpus",
"http://babelnet.org/rdf/s00024746n" -> "Encyclopedia",
"http://babelnet.org/rdf/s00025325n" -> "Data mining",
"http://babelnet.org/rdf/s00025333n" -> "Database",
"http://babelnet.org/rdf/s00025711n" -> "Decoder",
"http://babelnet.org/rdf/s00025751n" -> "Decoder",
"http://babelnet.org/rdf/s00026379n" -> "Repository",
"http://babelnet.org/rdf/s00026834n" -> "Spoken dialogue",
"http://babelnet.org/rdf/s00026967n" -> "Dictionary",
"http://babelnet.org/rdf/s00026967n" -> "Lexicon",
"http://babelnet.org/rdf/s00027425n" -> "Disambiguator",
"http://babelnet.org/rdf/s00027521n" -> "Discourse",
"http://babelnet.org/rdf/s00028142n" -> "Knowledge base",
"http://babelnet.org/rdf/s00031972n" -> "Evaluation",
"http://babelnet.org/rdf/s00031972n" -> "Evaluations",
"http://babelnet.org/rdf/s00031973n" -> "Evaluation",
"http://babelnet.org/rdf/s00035918n" -> "Part-of-speech",
"http://babelnet.org/rdf/s00041302n" -> "Grammar",
"http://babelnet.org/rdf/s00042116n" -> "Guidelines",
"http://babelnet.org/rdf/s00044916n" -> "Service",
"http://babelnet.org/rdf/s00045156n" -> "Language technology",
"http://babelnet.org/rdf/s00045819n" -> "Identification",
"http://babelnet.org/rdf/s00045936n" -> "Inference",
"http://babelnet.org/rdf/s00046973n" -> "Instrumental music",
"http://babelnet.org/rdf/s00047114n" -> "Translation",
"http://babelnet.org/rdf/s00049911n" -> "Speech",
"http://babelnet.org/rdf/s00049915n" -> "Terminology",
"http://babelnet.org/rdf/s00049918n" -> "Language learning",
"http://babelnet.org/rdf/s00050906n" -> "Lexical",
"http://babelnet.org/rdf/s00050906n" -> "Lexicon",
"http://babelnet.org/rdf/s00050969n" -> "Library",
"http://babelnet.org/rdf/s00050969n" -> "Toolkit",
"http://babelnet.org/rdf/s00052570n" -> "Machine translation system",
"http://babelnet.org/rdf/s00052570n" -> "Machine translation",
"http://babelnet.org/rdf/s00052570n" -> "Machine translator",
"http://babelnet.org/rdf/s00052570n" -> "Mt",
"http://babelnet.org/rdf/s00052570n" -> "Translation software",
"http://babelnet.org/rdf/s00054548n" -> "Metadata",
"http://babelnet.org/rdf/s00059033n" -> "Ontology",
"http://babelnet.org/rdf/s00059223n" -> "Optimization",
"http://babelnet.org/rdf/s00060604n" -> "Paraphraser",
"http://babelnet.org/rdf/s00060753n" -> "Parser",
"http://babelnet.org/rdf/s00060771n" -> "Pos tagger",
"http://babelnet.org/rdf/s00062898n" -> "Platform",
"http://babelnet.org/rdf/s00064421n" -> "Wordnet",
"http://babelnet.org/rdf/s00065988n" -> "Alignment",
"http://babelnet.org/rdf/s00066380n" -> "Learning software",
"http://babelnet.org/rdf/s00070404n" -> "Semantic",
"http://babelnet.org/rdf/s00070404n" -> "Semantics",
"http://babelnet.org/rdf/s00070532n" -> "Sentiment",
"http://babelnet.org/rdf/s00070651n" -> "Service",
"http://babelnet.org/rdf/s00070651n" -> "Services",
"http://babelnet.org/rdf/s00072892n" -> "Sound",
"http://babelnet.org/rdf/s00073275n" -> "Spell checker",
"http://babelnet.org/rdf/s00073508n" -> "Splitter",
"http://babelnet.org/rdf/s00073635n" -> "Stemmer",
"http://babelnet.org/rdf/s00075149n" -> "Summarization",
"http://babelnet.org/rdf/s00075728n" -> "Thesaurus",
"http://babelnet.org/rdf/s00075739n" -> "Grammar checker",
"http://babelnet.org/rdf/s00075891n" -> "Tagger",
"http://babelnet.org/rdf/s00076736n" -> "Text editor",
"http://babelnet.org/rdf/s00077585n" -> "Tools",
"http://babelnet.org/rdf/s00077958n" -> "Transcriber",
"http://babelnet.org/rdf/s00077963n" -> "Transcription",
"http://babelnet.org/rdf/s00079778n" -> "Verbs",
"http://babelnet.org/rdf/s00080130n" -> "Visualization",
"http://babelnet.org/rdf/s00081578n" -> "Workflow management",
"http://babelnet.org/rdf/s00091428v" -> "Paraphrasing",
"http://babelnet.org/rdf/s00140961n" -> "Semantic desktop",
"http://babelnet.org/rdf/s00279184n" -> "Data collection",
"http://babelnet.org/rdf/s00453702n" -> "Anaphora resolution",
"http://babelnet.org/rdf/s00683882n" -> "Semantic relatedness",
"http://babelnet.org/rdf/s00683882n" -> "Semantic similarities",
"http://babelnet.org/rdf/s00744700n" -> "Information retrieval system",
"http://babelnet.org/rdf/s00744700n" -> "Information retrieval",
"http://babelnet.org/rdf/s00899454n" -> "Natural language toolkit",
"http://babelnet.org/rdf/s00931937n" -> "Knowledge representation",
"http://babelnet.org/rdf/s00972833n" -> "Language processing",
"http://babelnet.org/rdf/s00978432n" -> "Language engineering",
"http://babelnet.org/rdf/s00987447n" -> "Knowledge acquisition",
"http://babelnet.org/rdf/s01042120n" -> "Formal grammar",
"http://babelnet.org/rdf/s01156701n" -> "Digital library",
"http://babelnet.org/rdf/s01184015n" -> "Language model",
"http://babelnet.org/rdf/s01184015n" -> "Language modeling",
"http://babelnet.org/rdf/s01184015n" -> "Language modelling",
"http://babelnet.org/rdf/s01184015n" -> "Language models",
"http://babelnet.org/rdf/s01236149n" -> "Statistical relational learning",
"http://babelnet.org/rdf/s01391386n" -> "Framenet",
"http://babelnet.org/rdf/s01647033n" -> "Machine learning",
"http://babelnet.org/rdf/s01832141n" -> "Online encyclopedia",
"http://babelnet.org/rdf/s02081302n" -> "Data analysis",
"http://babelnet.org/rdf/s02275757n" -> "Semantic network",
"http://babelnet.org/rdf/s02309436n" -> "Automatic speech recognition",
"http://babelnet.org/rdf/s02448439n" -> "Tool",
"http://babelnet.org/rdf/s02450977n" -> "Knowledge discovery",
"http://babelnet.org/rdf/s02570377n" -> "Ontology editor",
"http://babelnet.org/rdf/s02625608n" -> "Textual entailment",
"http://babelnet.org/rdf/s02817362n" -> "Database schema",
"http://babelnet.org/rdf/s02847697n" -> "Environment",
"http://babelnet.org/rdf/s03029246n" -> "Customization",
"http://babelnet.org/rdf/s03072994n" -> "Tool",
"http://babelnet.org/rdf/s03103884n" -> "CHILDES",
"http://babelnet.org/rdf/s03135597n" -> "Search engine",
"http://babelnet.org/rdf/s03145314n" -> "Conditional random fields",
"http://babelnet.org/rdf/s03171231n" -> "Speech segmentation",
"http://babelnet.org/rdf/s03171435n" -> "Text segmentation",
"http://babelnet.org/rdf/s03172404n" -> "Speech synthesis",
"http://babelnet.org/rdf/s03172404n" -> "Speech synthesiser",
"http://babelnet.org/rdf/s03172404n" -> "Text-to-speech synthesizer",
"http://babelnet.org/rdf/s03172404n" -> "Text-to-speech",
"http://babelnet.org/rdf/s03217333n" -> "Statistical machine translation",
"http://babelnet.org/rdf/s03217369n" -> "Word alignment",
"http://babelnet.org/rdf/s03325612n" -> "Statistical signal processing",
"http://babelnet.org/rdf/s03370016n" -> "Controlled language",
"http://babelnet.org/rdf/s03431217n" -> "Corpora",
"http://babelnet.org/rdf/s03431217n" -> "Corpus",
"http://babelnet.org/rdf/s03481372n" -> "Document summarization",
"http://babelnet.org/rdf/s03488992n" -> "Terminology management",
"http://babelnet.org/rdf/s03499081n" -> "Support vector machine",
"http://babelnet.org/rdf/s03631223n" -> "Content management system",
"http://babelnet.org/rdf/s03655374n" -> "Text simplification",
"http://babelnet.org/rdf/s03692686n" -> "Lexical analysis",
"http://babelnet.org/rdf/s03829842n" -> "Web service",
"http://babelnet.org/rdf/s03884071n" -> "N-gram"
  )
  // Linked datasets (this is only used for metadata but is created
  // on DB load). Not linked indicates URI starts which are not to 
  // be considered links, any other links are assumed to start with the 
  // server.
  val LINKED_SETS = List("http://dbpedia.org/")
  val NOT_LINKED = List("http://www.w3.org/", "http://purl.org/dc/",
    "http://xmlns.org/", "http://rdfs.org/", "http://schema.org/")
  // The minimum number of links to another dataset to be included in metadata
  val MIN_LINKS = 50
    
  // Metadata
 
  // The language of this site
  val LANG = "en"
  // If a resource in the data is the schema (ontology) then include its
  // path here. No intial slash, should resolve at BASE_NAME + ONTOLOGY
  val ONTOLOGY : Option[String] = None
  // The date the resource was created, e.g.,
  // The data should be of the format YYYY-MM-DD
  val ISSUE_DATE : Option[String] = None
  // The version number
  val VERSION_INFO : Option[String] = None
  // A longer textual description of the resource
  val DESCRIPTION : Option[String] = None
  // If using a standard license include the link to this license
  val LICENSE : Option[String] = None
  // Any keywords (if necessary)
  val KEYWORDS : Seq[String] = Nil
  // The publisher of the dataset
  val PUBLISHER_NAME : Option[String] = None
  val PUBLISHER_EMAIL : Option[String] = None
  // The creator(s) of the dataset
  // The lists must be the same size, use an empty string if you do not wish
  // to publish the email address
  val CREATOR_NAMES : Seq[String] = Nil
  val CREATOR_EMAILS : Seq[String] = Nil
  require(CREATOR_EMAILS.size == CREATOR_NAMES.size)
  // The contributor(s) to the dataset
  val CONTRIBUTOR_NAMES : Seq[String] = Nil
  val CONTRIBUTOR_EMAILS : Seq[String] = Nil
  require(CONTRIBUTOR_EMAILS.size == CONTRIBUTOR_NAMES.size)
  // Links to the resources this data set was derived from
  val DERIVED_FROM : Seq[String] = Nil
}
