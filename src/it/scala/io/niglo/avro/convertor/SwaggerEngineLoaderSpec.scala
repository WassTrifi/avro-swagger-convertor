package io.niglo.avro.convertor

import java.io.File
import java.nio.file.Paths
import org.scalatest._
import ITFixture._

class SwaggerEngineLoaderSpec extends FlatSpec with Matchers with GivenWhenThen {

  it  should "run the swaggerLoader and process the yaml file with correct schemas" in {

    Given("an input file with yaml specification and schemas to inject")
    val pathDir = Paths.get("specifications").resolve("swagger_spec.yaml")
    val inputFile = new File(this.getClass.getClassLoader.getResource(pathDir.toString).toURI)

    And("a specific path of output file ")
    val outputFile = new File(inputFile.getParentFile, "swagger_spec_out.yaml")

    When("a swagger loader run the process ")
    val engineLoader = new SwaggerEngineLoader()
    engineLoader.run(inputFile, outputFile)

    Then("the final swagger specification file is generated")
    assert(outputFile.exists() && outputFile.isFile)

  }

}
