package io.niglo.avro.convertor

import io.circe.yaml.parser.parse
import org.scalatest.EitherValues._
import org.scalatest.OptionValues._
object ITFixture {

  val PetsSchema = "avroSchemaPets.json"

  case class ArrayType(`type`: String = "array", items: Map[String,String])
  case class Pets(list: ArrayType)

  val linkArray =  ArrayType( items= Map[String,String]("$ref" -> "#/definitions/PetsLine"))
  val expectedPets  = Pets(linkArray)

  import io.circe._, io.circe.generic.semiauto._

  implicit val arrayDecoder: Decoder[ArrayType] = deriveDecoder[ArrayType]
  implicit val arrayEncoder: Encoder[ArrayType] = deriveEncoder[ArrayType]

  implicit val petsDecoder: Decoder[Pets] = deriveDecoder[Pets]
  implicit val petsEncoder: Encoder[Pets] = deriveEncoder[Pets]


  def schemas (yaml: String) : Option[Iterable[String]]= {
    val cursor = parse(yaml).right.value.hcursor
    cursor.downField("definitions").keys
  }

}
