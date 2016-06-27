package org.insightcentre.nlp.yuzu.csv

import com.opencsv.CSVReader
import org.insightcentre.nlp.yuzu.csv.schema._
import java.io.StringReader

object CSV2HTML {
  def readHeader(csvReader : CSVReader) : Seq[Column] = {
    val elems = csvReader.readNext()
    elems.toSeq.map({ s => Column(s) })
  }

  def convertTable(reader : String, table : Table) : String = {
    val csvReader = new CSVReader(new StringReader(reader),
        table.dialect.delimiter,
        table.dialect.quoteChar,
        '\\',
        table.dialect.skipRows,
        false,
        table.dialect.skipInitialSpace)
    val tableSchema = if(table.dialect.header && 
      table.tableSchema.columns.isEmpty) {
        table.tableSchema.copy(columns = readHeader(csvReader))
    } else {
      if(table.dialect.header) { 
        csvReader.readNext() // TODO: Probably shouldn't throw this away unchecked
      }
      table.tableSchema
    }
    convertTable(csvReader, tableSchema, table)
  }

  def convertTable(csvReader : CSVReader, tableSchema : TableSchema, table : Table) = {
    s"""<table class="csvTable table" id="csvTable">
      <thead>
        <tr>
          ${mkHeader(tableSchema)}
        </tr>
      </thead>
      <tbody>
      ${Stream.continually(csvReader.readNext()).takeWhile(_ != null).
        map(convertRow(_, tableSchema)).mkString("\n")}
      </tbody>
    </table>"""
  }

  def convertRow(row : Array[String], tableSchema : TableSchema) = {
    s"""<tr>${
      row.map({ v => s"""<td>$v</td>""" }).mkString("\n")
    }</tr>"""
  }

  def mkHeader(tableSchema : TableSchema) = {
    tableSchema.columns.map({ col =>
      col.propertyUrl match {
        case Some(url) =>
          s"""<th><a href="$url">${col.name}</a></th>"""
        case None =>
          s"""<th>${col.name}</th>"""
      }
    }).mkString("\n")
  }
}
