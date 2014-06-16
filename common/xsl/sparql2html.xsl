<?xml version="1.0"?>
<!DOCTYPE xsl:stylesheet [
    <!ENTITY base    "${base}">
    <!ENTITY ontology "${base}ontology#">
    <!ENTITY prefix1 "${prefix1uri}">
    <!ENTITY prefix2 "${prefix2uri}">
    <!ENTITY prefix3 "${prefix3uri}">
    <!ENTITY prefix4 "${prefix4uri}">
    <!ENTITY prefix5 "${prefix5uri}">
    <!ENTITY prefix6 "${prefix6uri}">
    <!ENTITY prefix7 "${prefix7uri}">
    <!ENTITY prefix8 "${prefix8uri}">
    <!ENTITY prefix9 "${prefix9uri}">
    <!ENTITY rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <!ENTITY rdfs "http://www.w3.org/2000/01/rdf-schema#">
    <!ENTITY owl "http://www.w3.org/2002/07/owl#">
    ]>
 <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:sparql="http://www.w3.org/2005/sparql-results#"
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" >

    <xsl:template match="/sparql:sparql">
        <table class="sparql">
            <xsl:apply-templates select="sparql:head"/>
            <tbody>
                <xsl:apply-templates select="sparql:results"/>
            </tbody>
        </table>
    </xsl:template>

    <xsl:template match="sparql:head">
        <thead>
            <tr class="sparql_head">
                <xsl:for-each select="sparql:variable">
                    <th class="sparql_head"><xsl:value-of select="@name"/></th>
                </xsl:for-each>
            </tr>
        </thead>
    </xsl:template>

    <xsl:template match="sparql:results">
        <xsl:apply-templates select="sparql:result"/>
    </xsl:template>

    <xsl:template match="sparql:result">
        <xsl:variable name="row" select="."/>
        <tr class="sparql_body">
            <xsl:for-each select="//sparql:sparql/sparql:head/sparql:variable">
                <td class="sparql_body">
                    <xsl:variable name="var" select="@name"/>
                    <xsl:choose>
                        <xsl:when test="$$row/sparql:binding[@name=$$var]">
                            <xsl:apply-templates select="$$row/sparql:binding[@name=$$var]"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <i>Unbound</i>
                        </xsl:otherwise>
                    </xsl:choose>
                </td>
            </xsl:for-each>
        </tr>
    </xsl:template>

    <xsl:template match="sparql:binding">
        <xsl:apply-templates select="sparql:uri"/>
        <xsl:apply-templates select="sparql:literal"/>
        <xsl:apply-templates select="sparql:bnode"/>
    </xsl:template>

    <xsl:template match="sparql:uri">
        <a class="sparql_uri">
            <xsl:attribute name="href">
                <xsl:value-of select="."/>
            </xsl:attribute>
            <xsl:choose>
                <xsl:when test="contains(.,'&base;')">
                    <xsl:value-of select="substring-after(.,'&base;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&ontology;')">
                    <xsl:value-of select="substring-after(.,'&ontology;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&rdf;')">
                    <xsl:value-of select="concat('rdf:',substring-after(.,'&rdf;'))"/>
                </xsl:when>
                <xsl:when test="contains(.,'&rdfs;')">
                    <xsl:value-of select="concat('rdfs:',substring-after(.,'&rdfs;'))"/>
                </xsl:when>
                <xsl:when test="contains(.,'&owl;')">
                    <xsl:value-of select="concat('owl:',substring-after(.,'&owl;'))"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix1;')">
                    <xsl:value-of select="concat('${prefix1qn}:',substring-after(.,'&prefix1;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix2;')">
                    <xsl:value-of select="concat('${prefix2qn}:',substring-after(.,'&prefix2;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix3;')">
                    <xsl:value-of select="concat('${prefix3qn}:',substring-after(.,'&prefix3;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix4;')">
                    <xsl:value-of select="concat('${prefix4qn}:',substring-after(.,'&prefix4;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix5;')">
                    <xsl:value-of select="concat('${prefix5qn}:',substring-after(.,'&prefix5;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix6;')">
                    <xsl:value-of select="concat('${prefix6qn}:',substring-after(.,'&prefix6;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix7;')">
                    <xsl:value-of select="concat('${prefix7qn}:',substring-after(.,'&prefix7;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix8;')">
                    <xsl:value-of select="concat('${prefix8qn}:',substring-after(.,'&prefix8;')"/>
                </xsl:when>
                <xsl:when test="contains(.,'&prefix9;')">
                    <xsl:value-of select="concat('${prefix9qn}:',substring-after(.,'&prefix9;')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="."/>
                </xsl:otherwise>
            </xsl:choose>
        </a>
    </xsl:template>

    <xsl:template match="sparql:literal">
        <span class="sparql_literal">
            <xsl:if test="@xml:lang">
                <xsl:attribute name="xml:lang">
                    <xsl:value-of select="@xml:lang"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:value-of select="."/>
        </span>
    </xsl:template>

    <xsl:template match="sparql:bnode">
        <span class="sparql_bnode">
            <xsl:value-of select="."/>
        </span>
    </xsl:template>
</xsl:stylesheet>

