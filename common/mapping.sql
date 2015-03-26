/* First ensure the following files are unzipped:
      wn30-31.csv
      wn20-30.csv
   Then execute this script as follows:
      sqlite3 ../scala/mapping.db < mapping.sql
*/
CREATE TABLE wn30 ( wn31 int, wn30 varchar(80));
CREATE INDEX mapping_wn30 on wn30 (wn30);
CREATE TABLE wn20 ( wn20 varchar(10), wn30 varchar(10) );
CREATE INDEX mapping_wn20_20 on wn20 (wn20);
CREATE INDEX mapping_wn20_30 on wn20 (wn30);
.mode csv
.import wn30-31.csv wn30
.import wn20-30.csv wn20

