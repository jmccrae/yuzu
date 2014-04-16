<?xml version="1.0"?>

<!DOCTYPE xsl:stylesheet [
    <!ENTITY lemon   "http://lemon-model.net/lemon#">
    <!ENTITY base    "${base}">
    <!ENTITY ontology "${base}ontology#">
]>
 
<xsl:stylesheet version="1.0"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
xmlns:base="${base}"
xmlns:ontology="${base}ontology">

  <xsl:strip-space elements="*"/>
  <xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>

  <!-- Modify this template for URIs specified in full with @rdf:resource -->  
  <xsl:template name="display-uri">
    <xsl:param name="text"/>
    <xsl:choose>
      <xsl:when test="contains($$text,'&base;')">
        <xsl:value-of select="substring-after($$text,'&base;')"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$$text"/>
      </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:variable name="apos">'</xsl:variable>
  
<xsl:template match="/rdf:RDF">  
  <p>
  <xsl:choose>
    <xsl:when test="count(*[substring-after(@rdf:about,'#')='']) = 1">
     <xsl:for-each select="*[substring-after(@rdf:about,'#')='']">
         <h1>
             <xsl:call-template name="display-uri">
                 <xsl:with-param name="text" select="@rdf:about"/>
             </xsl:call-template>
             <img src="/assets/rdf_w3c_icon.48.gif" height="28px" onclick="document.getElementById('rdf_format_list').style.display='inline';" style="float:right;"/>
        </h1>
        <xsl:call-template name="rdf_links"/>
        <!--


             <xsl:value-of select="@rdf:about"/>
         &#160;&#160;
         <a>
           <xsl:attribute name="href">
             <xsl:value-of select="concat(@rdf:about,'.rdf')"/>
           </xsl:attribute>
           <img src="/img/rdf_flyer.png"/>
         </a>
         <a>
           <xsl:attribute name="href">
             <xsl:value-of select="concat(@rdf:about,'.ttl')"/>
           </xsl:attribute>
           <img src="/img/icon_turtle.gif"/>
         </a>
       </h2>
       <h5>Instance of: <a property="http://www.w3.org/1999/02/22-rdf-syntax-ns#type">
           <xsl:attribute name="href">
           <xsl:value-of select="concat(namespace-uri(),local-name())"/>
           </xsl:attribute>
           <xsl:value-of select="name()"/>
           </a>
       </h5>-->
       <xsl:call-template name="forprop"/>
    </xsl:for-each>
    </xsl:when>
    <xsl:otherwise>
     <xsl:for-each select="*[substring-after(@rdf:about,'#')='']">
       <h2><xsl:value-of select="@rdf:about"/></h2>
       <h5>Instance of: <a property="http://www.w3.org/1999/02/22-rdf-syntax-ns#type">
           <xsl:attribute name="href">
           <xsl:value-of select="concat(namespace-uri(),local-name())"/>
           </xsl:attribute>
           <xsl:value-of select="name()"/>
           </a>
       </h5>
       <xsl:call-template name="forprop"/>
    </xsl:for-each>
    </xsl:otherwise>
  </xsl:choose>
  </p>
</xsl:template>

<xsl:template name="forprop">
  <table class="table table-hover rdf rdf_main">
     <tr>
       <th>Property</th>
       <th>Value</th>
     </tr>
     <xsl:for-each select="*">
       <tr class="active">
          <td> <a>
         <xsl:attribute name="href">
         <xsl:value-of select="concat(namespace-uri(),local-name())"/>
         </xsl:attribute>
         <xsl:value-of select="name()"/>
         </a>
         </td>
         <td>
            <xsl:choose>
              <xsl:when test="@rdf:resource">
                <xsl:variable name="rdfResource" select="@rdf:resource"/>
                <xsl:choose>
                <xsl:when test="not(substring-after(@rdf:resource,'#')='') and //*[@rdf:about=$$rdfResource]">
                  <span>
                    <xsl:attribute name="id">
                      <xsl:value-of select="substring-after(@rdf:resource,'#')"/>
                    </xsl:attribute>
                    <xsl:attribute name="resource">
                      <xsl:value-of select="concat('#',substring-after(@rdf:resource,'#'))"/>
                    </xsl:attribute>
                    <xsl:attribute name="rel">
                      <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                    </xsl:attribute>
		    <i><xsl:value-of select="substring-after(@rdf:resource,'#')"/></i>
                    <xsl:for-each select="//*[@rdf:about=$$rdfResource]">
                      <xsl:call-template name="forprop2"/>
                    </xsl:for-each>
                  </span>
                </xsl:when>
                <xsl:otherwise>
                <a>
                 <xsl:attribute name="href">
                 <xsl:value-of select="@rdf:resource"/>
                 </xsl:attribute>
                 <xsl:attribute name="property">
                   <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                 </xsl:attribute>
                 <xsl:call-template name="display-uri">
                   <xsl:with-param name="text" select="@rdf:resource"/>
                 </xsl:call-template>
                </a>
                <xsl:if test="not(starts-with(@rdf:resource,'http'))">
                  &#160;&#160;&#160;
                  <a class="load_entry"><xsl:attribute name="href">
                    <xsl:value-of select="concat('javascript:ajax_load_entry(',$$apos,@rdf:resource,$$apos,')')"/>
                  </xsl:attribute>More...</a>
                  <div style="display:none;">
                    <xsl:attribute name="id">
                      <xsl:value-of select="concat('la_',translate(@rdf:resource,':$$','__'))"/>
                    </xsl:attribute>
                  </div>
                </xsl:if>
                </xsl:otherwise>
                </xsl:choose>
              </xsl:when>
              <xsl:when test="node()[last()]/self::text()">
                 <xsl:call-template name="lang">
                     <xsl:with-param name="lang_id" select="@xml:lang"/>
                 </xsl:call-template> 
                 &#x201c;<span>
                 <xsl:attribute name="property">
                  <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                 </xsl:attribute><xsl:value-of select="node()"/></span>&#x201d;
              </xsl:when>
	      <xsl:when test="rdf:Description/rdf:first">
	        <ol>
		  <xsl:for-each select="rdf:Description">
  		    <xsl:call-template name="list"/>
		  </xsl:for-each>
		</ol>
	      </xsl:when>
              <xsl:otherwise>
                <span typeof="http://www.w3.org/2002/07/owl#Thing">
                 <xsl:attribute name="property">
                  <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                 </xsl:attribute>
                <xsl:for-each select="*">
                  <xsl:call-template name="forprop2"/>
                </xsl:for-each>
                </span>
              </xsl:otherwise>
            </xsl:choose>
         </td>
       </tr>
    </xsl:for-each>
  </table>
</xsl:template>


<xsl:template name="forprop2">
  <table class="rdf table">
     <xsl:if test="not(name()='rdf:Description')">
       <tr>
         <td> <a href="http://www.w3.org/1999/02/22-rdf-syntax-ns#type">rdf:type</a></td>
         <td> <a property="http://www.w3.org/1999/02/22-rdf-syntax-ns#type">
         <xsl:attribute name="href">
         <xsl:value-of select="concat(namespace-uri(),local-name())"/>
         </xsl:attribute>
         <xsl:value-of select="name()"/>
         </a></td>
       </tr>
     </xsl:if>
     <xsl:for-each select="*">
       <tr>
          <td> <a>
         <xsl:attribute name="href">
         <xsl:value-of select="concat(namespace-uri(),local-name())"/>
         </xsl:attribute>
         <xsl:value-of select="name()"/>
         </a>
         </td>
         <td>
            <xsl:choose>
              <xsl:when test="@rdf:resource">
                <xsl:variable name="rdfResource" select="@rdf:resource"/>
                <xsl:choose>
                <xsl:when test="not(substring-after(@rdf:resource,'#')='') and //*[@rdf:about=$$rdfResource]">
                  <span>
                    <xsl:attribute name="id">
                      <xsl:value-of select="substring-after(@rdf:resource,'#')"/>
                    </xsl:attribute>
                    <xsl:attribute name="resource">
                      <xsl:value-of select="concat('#',substring-after(@rdf:resource,'#'))"/>
                    </xsl:attribute>
                    <xsl:attribute name="rel">
                      <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                    </xsl:attribute>
		    <i><xsl:value-of select="substring-after(@rdf:resource,'#')"/></i>
                    <xsl:for-each select="//*[@rdf:about=$$rdfResource]">
                      <xsl:call-template name="forprop2"/>
                    </xsl:for-each>
                  </span>
                </xsl:when>
                <xsl:otherwise>
                <a>
                 <xsl:attribute name="href">
                 <xsl:value-of select="@rdf:resource"/>
                 </xsl:attribute>
                 <xsl:attribute name="property">
                  <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                 </xsl:attribute>
                 <xsl:call-template name="display-uri">
                   <xsl:with-param name="text" select="@rdf:resource"/>
                 </xsl:call-template>
                </a>
                <xsl:if test="not(starts-with(@rdf:resource,'http'))">
                  &#160;&#160;&#160;
                  <a class="load_entry"><xsl:attribute name="href">
                    <xsl:value-of select="concat('javascript:ajax_load_entry(',$$apos,@rdf:resource,$$apos,')')"/>
                  </xsl:attribute>More...</a>
                  <div style="display:none;">
                    <xsl:attribute name="id">
                      <xsl:value-of select="concat('la_',translate(@rdf:resource,'$$:','__'))"/>
                    </xsl:attribute>
                  </div>
                </xsl:if>
                </xsl:otherwise>
                </xsl:choose>
              </xsl:when>
              <xsl:when test="node()[last()]/self::text()">  
                  <xsl:call-template name="lang">
                     <xsl:with-param name="lang_id" select="@xml:lang"/>
                 </xsl:call-template> 
                 &#x201c;<span>
                 <xsl:attribute name="property">
                  <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                 </xsl:attribute><xsl:value-of select="node()"/></span>&#x201d;
              </xsl:when>
	      <xsl:when test="rdf:Description/rdf:first">
	        <ol>
		  <xsl:for-each select="rdf:Description">
  		    <xsl:call-template name="list"/>
		  </xsl:for-each>
		</ol>
	      </xsl:when>
              <xsl:otherwise>
                <span typeof="http://www.w3.org/2002/07/owl#Thing">
                <xsl:attribute name="property">
                  <xsl:value-of select="concat(namespace-uri(),local-name())"/>
                </xsl:attribute>
                <xsl:for-each select="*">
                  <xsl:call-template name="forprop2"/>
                </xsl:for-each>
                </span>
              </xsl:otherwise>
            </xsl:choose>
         </td>
       </tr>
    </xsl:for-each>
  </table>
</xsl:template>

<xsl:template name="list">
  <li>
    <xsl:choose>
      <xsl:when test="rdf:first/@rdf:resource">
        <a>
	  <xsl:attribute name="href">
	    <xsl:value-of select="rdf:first/@rdf:resource"/>
	  </xsl:attribute>
          <xsl:attribute name="property">
            <xsl:value-of select="rdf:first/@rdf:resource"/>
          </xsl:attribute>
          <xsl:call-template name="display-uri">
            <xsl:with-param name="text" select="rdf:first/@rdf:resource"/>
          </xsl:call-template>
        </a>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="forprop2" select="rdf:first"/>
      </xsl:otherwise>
    </xsl:choose>
  </li>
  <xsl:choose>
    <xsl:when test="rdf:rest/@rdf:resource='http://www.w3.org/1999/02/22-rdf-syntax-ns#nil'">
    
    </xsl:when>
    <xsl:when test="rdf:rest/rdf:Description">
      <xsl:for-each select="rdf:rest/rdf:Description">
        <xsl:call-template name="list"/>
      </xsl:for-each>
    </xsl:when>
  </xsl:choose>
</xsl:template>

<xsl:template name="lang">
    <xsl:param name="lang_id"/>
    <xsl:variable name="lang-id" select="translate($$lang_id,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/>
    <xsl:choose>
        <xsl:when test="$$lang-id='en' or $$lang-id='eng'">
            <img src="/assets/flag/en.gif" alt="eng"/>
        </xsl:when>
        <xsl:when test="$$lang-id='en-us' or $$lang-id='eng-us'">
            <img src="/assets/flag/us.gif" alt="eng-US"/>
        </xsl:when>
        <xsl:when test="$$lang-id='en-gb' or $$lang-id='eng-gb'">
            <img src="/assets/flag/gb.gif" alt="eng-GB"/>
        </xsl:when>
        <xsl:when test="$$lang-id='en-gb' or $$lang-id='eng-gb'">
            <img src="/assets/flag/gb.gif" alt="eng-GB"/>
        </xsl:when>
        <xsl:when test="$$lang-id='zh' or $$lang-id='chi' or $$lang-id='zho'">
            <img src="/assets/flag/cn.gif" alt="zho"/>
        </xsl:when>
        <xsl:when test="$$lang-id='zh-tw' or $$lang-id='zho-tw'">
            <img src="/assets/flag/tw.gif" alt="zho-TW"/>
        </xsl:when>
        <xsl:when test="$$lang-id='zh-cn' or $$lang-id='zho-cn'">
            <img src="/assets/flag/cn.gif" alt="zho-CN"/>
        </xsl:when>
        <xsl:when test="$$lang-id='es' or $$lang-id='spa'">
            <img src="/assets/flag/es.gif" alt="spa"/>
        </xsl:when>
        <xsl:when test="$$lang-id='ar' or $$lang-id='ara'">
            <img src="/assets/flag/sa.gif" alt="ara"/>
        </xsl:when>
        <xsl:when test="$$lang-id='pt' or $$lang-id='por'">
            <img src="/assets/flag/pt.gif" alt="por"/>
        </xsl:when>
        <xsl:when test="$$lang-id='bn' or $$lang-id='ben'">
            <img src="/assets/flag/bd.gif" alt="ben"/>
        </xsl:when>
        <xsl:when test="$$lang-id='ru' or $$lang-id='rus'">
            <img src="/assets/flag/ru.gif" alt="rus"/>
        </xsl:when>
        <xsl:when test="$$lang-id='ja' or $$lang-id='jap'">
            <img src="/assets/flag/jp.gif" alt="jap"/>
        </xsl:when>
        <xsl:when test="$$lang-id='de' or $$lang-id='deu' or $$lang-id='ger'">
            <img src="/assets/flag/de.gif" alt="deu"/>
        </xsl:when>
        <xsl:when test="$$lang-id='ko' or $$lang-id='kor'">
            <img src="/assets/flag/kr.gif" alt="kor"/>
        </xsl:when>
        <xsl:when test="$$lang-id='fr' or $$lang-id='fra'">
            <img src="/assets/flag/fr.gif" alt="fra"/>
        </xsl:when>
        <xsl:when test="$$lang-id='it' or $$lang-id='ita'">
            <img src="/assets/flag/it.gif" alt="ita"/>
        </xsl:when>
        <xsl:when test="$$lang-id='pl' or $$lang-id='pol'">
            <img src="/assets/flag/pl.gif" alt="pol"/>
        </xsl:when>
        <xsl:when test="$$lang-id='uk' or $$lang-id='ukr'">
            <img src="/assets/flag/ua.gif" alt="ukr"/>
        </xsl:when>
        <xsl:when test="$$lang-id='ro' or $$lang-id='rum' or $$lang-id='ron'">
            <img src="/assets/flag/ro.gif" alt="ron"/>
        </xsl:when>
        <xsl:when test="$$lang-id='nl' or $$lang-id='dut' or $$lang-id='nld'">
            <img src="/assets/flag/nl.gif" alt="nld"/>
        </xsl:when>
        <xsl:when test="$$lang-id='hu' or $$lang-id='hun'">
            <img src="/assets/flag/hu.gif" alt="hun"/>
        </xsl:when>
        <xsl:when test="$$lang-id='cs' or $$lang-id='cze' or $$lang-id='ces'">
            <img src="/assets/flag/cz.gif" alt="ces"/>
        </xsl:when>
        <xsl:when test="$$lang-id='da' or $$lang-id='dan'">
            <img src="/assets/flag/dk.gif" alt="dan"/>
        </xsl:when>
        <xsl:when test="$$lang-id='sv' or $$lang-id='swe'">
            <img src="/assets/flag/se.gif" alt="swe"/>
        </xsl:when>
        <xsl:when test="$$lang-id='no' or $$lang-id='nor'">
            <img src="/assets/flag/no.gif" alt="nor"/>
        </xsl:when>
        <xsl:when test="$$lang-id='fi' or $$lang-id='fin'">
            <img src="/assets/flag/fi.gif" alt="fin"/>
        </xsl:when>
        <xsl:when test="$$lang-id='el' or $$lang-id='ell' or $$lang-id='gre'">
            <img src="/assets/flag/gr.gif" alt="ell"/>
        </xsl:when>
        <xsl:otherwise><b><xsl:value-of select="$$lang-id"/></b></xsl:otherwise>
    </xsl:choose>
</xsl:template>
    <xsl:template name="rdf_links">
            <ul id="rdf_format_list">
                <li class="rdf_format">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:value-of select="concat(substring-after(@rdf:about,'&base;'),'.json')"/>
                        </xsl:attribute>
                        JSON-LD
                    </a>
                </li>
                <li class="rdf_format">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:value-of select="concat(substring-after(@rdf:about,'&base;'),'.nt')"/>
                        </xsl:attribute>
                        N-Triples
                    </a>
                </li>
                <li class="rdf_format">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:value-of select="concat(substring-after(@rdf:about,'&base;'),'.ttl')"/>
                        </xsl:attribute>
                        Turtle
                    </a>
                </li>
                <li class="rdf_format">
                    <a>
                        <xsl:attribute name="href">
                            <xsl:value-of select="concat(substring-after(@rdf:about,'&base;'),'.rdf')"/>
                        </xsl:attribute>
                        RDF/XML
                    </a>
                </li>
            </ul>
    </xsl:template>


</xsl:stylesheet>
