package io.niglo.avro.convertor

import java.nio.file.Paths
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}


/** TO USE ONLY FOR IT TESTS
  *  - Read a json file with an avro representation of a schema
  *  - Serve the content for the Parser
 */

object  JsonSchemaReaderSupport {
  private val logger = LoggerFactory.getLogger(this.getClass.getName)

  val DirSource = "schema"
  val PetsSchema = "avroSchemaPets.json"

  def readSchemaFile(schemaFile: String ) : String = {

    val refPath = Try {
      val path = Paths.get(DirSource).resolve(schemaFile)
      this.getClass.getClassLoader.getResource(path.toString)
    }

    logger.debug(s" the final resulting path for the source Json File is -- ${refPath} --")

    val content = for {
      filePath ← refPath
      fileUri ← Try(filePath.toURI)
    } yield scala.io.Source.fromFile(fileUri)

    lazy val contentAsString: String = content map {
      _.getLines().mkString
    } match {
      case Success(c) ⇒
        logger.info(s"Successfully read file {} to plain text content!", schemaFile)
        c

      case Failure(error) ⇒
        logger.error(s"Failed to read content from source file {} ", schemaFile)
        throw error
    }

    contentAsString
  }


}



