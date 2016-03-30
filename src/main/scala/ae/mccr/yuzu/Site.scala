package ae.mccr.yuzu

import java.io.File

trait Site {
  /**
   * Where the templates can be found
   */
  def templates : Option[File]

  /**
   * The data file as a zip
   */
  def data : File

  /**
   * The index where this site lives
   */
  def index : String
}
