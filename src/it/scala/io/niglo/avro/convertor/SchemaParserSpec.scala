package io.niglo.avro.convertor

import io.niglo.avro.convertor.ITFixture._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import io.circe.yaml.parser._
import org.scalatest.EitherValues._
import cats.syntax.either._
import io.niglo.avro.convertor.ITFixture.{Pets,}
import org.scalatest.OptionValues._


class SchemaParserSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

   import JsonSchemaReaderSupport._

}

