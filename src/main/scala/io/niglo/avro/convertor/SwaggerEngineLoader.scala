package io.niglo.avro.convertor

import java.io.{File, PrintWriter}
import java.net.{URL, URLClassLoader}
import java.util

import com.typesafe.config.ConfigFactory
import io.swagger.converter.ModelConverters
import org.apache.commons.io.FileUtils
import org.fusesource.scalate.{TemplateEngine, TemplateSource}
import org.joda.money.Money
import org.slf4j.LoggerFactory

/**  Load definitions section of a swagger specification file with avro schemas
  *
  * <p>
  *    you can include this Loader as :
  *    {{
  *      val engineLoader = new SwaggerEngineLoader()
  *      val inputFile = new File("path/to/file/Name")
  *      val outputFile = new File("path/to/file/Name")
  *      engineLoader.run(inputFile,outputFile)
  *    }}
  * </p>
  *
  */
class SwaggerEngineLoader {

  private val logger = LoggerFactory.getLogger(this.getClass.getName)

  private val _baseDir = ConfigFactory.load().getString("user.dir")

  private val engine = new TemplateEngine

  private val workingDir = new File(_baseDir, "target/test-data/" + getClass.getSimpleName.stripSuffix("$"))

  cleanWorkingDirectory()

  engine.workingDirectory = workingDir
  engine.allowReload = true
  engine.allowCaching = false
  engine.importStatements ++= List("import io.niglo.avro.convertor.SwaggerEngineLoader._")


  private def cleanWorkingDirectory() = if (workingDir.exists()) {
    FileUtils.deleteDirectory(workingDir)
  }


  /** generate template from bound variables of each schema object description
    *
    * @param inputFile swagger specification file including the schemas to retrieve
    * @param outputFile swagger file including schemas definitions
    */
  def run(inputFile: File, outputFile: File): Unit = {

    val source = TemplateSource.fromFile(inputFile.getAbsoluteFile).templateType("ssp")

    logger.info("Processing {} as an input file", inputFile.getAbsoluteFile)
    val swagger = engine.layout(source)
    val writer = new PrintWriter(outputFile)
    writer.write(swagger)
    writer.close()
    cleanWorkingDirectory()
    logger.info("Swagger conversion completed successfully. You can find generated files under {}", outputFile.getAbsolutePath)
  }
}

object SwaggerEngineLoader {

  import scala.collection.JavaConverters._


  private val config = ConfigFactory.load().getConfig("phenix")
  private val logger = LoggerFactory.getLogger(classOf[SwaggerEngineLoader])



  println(s"got the config files = ", config)

  /** inject schemas from avro definitions to the swagger spec file.
    *
    * @param schemas requested schemas by version
    * @param schemaMangagerUrl optionally schema manager address to invoke
    * @return
    */
  def reload(schemas: Map[String, Int], schemaMangagerUrl: Option[String] = None): String = {

    val fetcher = new SchemaFetcher(config)
    if (schemas.isEmpty) {
      throw new IllegalArgumentException("Empty list of schemas in the swagger template definition")
    } else fetcher.jsonSchemas(schemas).map { schema ⇒
      logger.info("Processing conversion of schema {} to swagger definition and Type is", schema.getFullName)
      val parser = new SchemaParser(schema.toString)
      parser.asObject
    }.reduceLeft(_ merge _).toYaml
  }
}

