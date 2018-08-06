package io.niglo.avro.convertor

import io.niglo.avro.convertor.SchemaParser._
import io.circe._
import org.scalatest._
import io.circe.syntax._
import io.circe.parser._
import io.circe.Encoder
import io.niglo.avro.convertor.SchemaParser._

class SchemaParserTest extends FlatSpec
    with Matchers
    with Inside
    with OptionValues {

  import Fixture._

  it should "decode the json schema as a record Object" in {
    val parser = new SchemaParser(SchemaRecord)
    val schema = parser.parseSchema
    schema shouldBe a[AvroObject]
  }

  it should "decode a record with an  Enum object as Type" in {
    val parser = new SchemaParser(ObjectSchemaType_1)
    val schema = parser.parseSchema
    schema shouldBe a[AvroJsonObject]
    inside(schema) {
      case AvroJsonObject(enum, name, default) ⇒
        enum shouldBe a[AvroEnumObject]
    }
  }

  it should "decode json schema with simple Union type" in {
    val parser = new SchemaParser(UnionSchemaType)
    val schema = parser.parseSchema
    schema shouldBe a[AvroSimpleUnionObject]
    inside(schema) {
      case AvroSimpleUnionObject(recordType, _, _, _) ⇒
        recordType shouldBe a[Array[String]]
    }
  }

  it should "decode json schema with an Array type" in {
    val parser = new SchemaParser(SchemaArrayType)
    val schema = parser.parseSchema
    schema shouldBe a[AvroArrayType]
    inside(schema) {
      case AvroArrayType(recordType, name, items, _, _) ⇒
        recordType shouldBe "array"
        items shouldBe a[AvroObject]
    }
  }

  import cats.syntax.either._
  import GenericDerivation._

  it should "decode json schema with an Array as a  Union  Type " in {
    val parser = new SchemaParser(UnionSchemaWithArrayType)
    val schema = parser.parseSchema
    schema shouldBe a[AvroComponentUnionObject]
    inside(schema) {
      case AvroComponentUnionObject(recordType, _, _, _) ⇒
        recordType should have size 2
        val jsonType = recordType.filter(_.isObject).head
        jsonType shouldBe a[Json]
        val parsed: AvroJsonRecord = jsonType.as[AvroJsonRecord].valueOr(throw _)
        parsed shouldBe a[AvroArrayType]
    }
  }

  it should "decode json schema with an Array as an Object Type " in {

    val parser = new SchemaParser(UnionSchemaWithArrayType_2)
    val schema = parser.parseSchema
    schema shouldBe a[AvroComponentUnionObject]
    inside(schema) {
      case AvroComponentUnionObject(recordType, _, _, _) ⇒
        recordType should have size 1
        val jsonType = recordType.filter(_.isObject).head
        jsonType shouldBe a[Json]
        val parsed: AvroJsonRecord = jsonType.as[AvroJsonRecord].valueOr(throw _)
        parsed shouldBe a[AvroArrayType]
    }
  }

  it should "decode and parse to a An array object " in {
    val parser = new SchemaParser(SchemaArrayType_2)
    val schema = parser.parseSchema
    schema shouldBe a[AvroArrayType]
  }

  it should "raise an exception when parsing a bad json avro schema" in {
    intercept[ParsingException] {
      val parserWithError = new SchemaParser(BadSchemaJson)
      parserWithError.parseSchema
    }
  }

  it should "decode json record as complete schema " in {
    val parser = new SchemaParser(SchemaRecord_2)
    val schema = parser.parseSchema
    schema shouldBe a[AvroObject]

    inside(schema) {
      case AvroObject(_, _, _, _, fields) ⇒
        fields should have size 4
        val mayBeEnum = fields.filter(f ⇒ f.isDefined && f.get.isInstanceOf[AvroJsonObject]).head
        mayBeEnum shouldBe defined
        mayBeEnum.value.asInstanceOf[AvroJsonObject].`type` shouldBe a[AvroEnumObject]
    }
  }

  it should "decode json schema with an Record of type Enum as a Union type" in {

    val parser = new SchemaParser(UnionSchemaTypeWithObject)
    val schema = parser.parseSchema
    schema shouldBe a[AvroComponentUnionObject]
    inside(schema) {
      case AvroComponentUnionObject(recordType, _, _, _) ⇒
        recordType should have size 2
        val enum = recordType.filter(_.isObject).head
        val parsed = enum.as[AvroJsonRecord].valueOr(throw _)
        parsed shouldBe a[AvroEnumObject]
        parsed.asInstanceOf[AvroEnumObject].symbols shouldBe Array("EUR", "USD")
    }
  }

  it should "decode json schema field as an Enum type" in {
    val parser = new SchemaParser(EnumSchemaType)
    val schema = parser.parseSchema
    schema shouldBe a[AvroEnumObject]
  }

  it should "decode a json schema with a record object as Union Type" in {
    val parser = new SchemaParser(UnionSchemaWithRecordType)
    val schema = parser.parseSchema
    schema shouldBe a[AvroComponentUnionObject]
    inside(schema) {
      case AvroComponentUnionObject(recordType, _, _, _) ⇒
        recordType should have size 2
        val record = recordType.filter(_.isObject).head
        val parsed = record.as[AvroJsonRecord].valueOr(throw _)
        parsed shouldBe a[AvroObject]
    }

  }

  it should "decode a json schema  with a record object as Type" in {
    val parser = new SchemaParser(ObjectSchemaType_2)
    val schema = parser.parseSchema
    schema shouldBe a[AvroJsonObject]
    inside(schema) {
      case AvroJsonObject(record, _, _) ⇒
        record shouldBe a[AvroObject]
    }

  }

  it must "Decode and encode a an AvroObject class with field having more than one type" in {

    case class Sample(name: String, default: Json)

    val sampleJsonOut =
      s"""|{
          |  "name" : "Sample with different type",
          |  "default" : "true"
          |}""".stripMargin

    val sampleJsonWithBoolean =
      s"""|{
          |  "name" : "Sample with Boolean type",
          |  "default" : true
          |}""".stripMargin

    val sampleJsonWithInteger =
      s"""|{
          |  "name" : "Sample with Integer type",
          |  "default" : 0
          |}""".stripMargin

    val sampleJsonWithOutField =
      s"""|{
          |  "name" : "Sample with Void type"
          |}""".stripMargin

    import cats.syntax.either._
    import scala.util.Try

    implicit val decodeSample: Decoder[Sample] = new Decoder[Sample] {
      final def apply(c: HCursor): Decoder.Result[Sample] = {
        for {
          foo ← c.downField("name").as[String]
        } yield {
          Sample(foo, c.downField("default").focus.getOrElse(Json.Null))
        }
      }
    }

    implicit val encodeSample: Encoder[Sample] = new Encoder[Sample] {
      final def apply(a: Sample): Json = {
        val v = Try(a.default.toString).getOrElse("null")
        Json.obj(
          ("name", Json.fromString(a.name)),
          ("default", v.asJson)
        )
      }
    }

    val decodedBool: Sample = decode[Sample](sampleJsonWithBoolean).valueOr(throw _)
    val decodedInt: Sample = decode[Sample](sampleJsonWithInteger).valueOr(throw _)

    val decodedVoid: Sample = decode[Sample](sampleJsonWithOutField).valueOr(throw _)

    inside(decodedBool) {
      case Sample(name, v) ⇒
        name shouldBe "Sample with Boolean type"
        v shouldBe true.asJson
    }

    inside(decodedInt) {
      case Sample(name, v) ⇒
        name shouldBe "Sample with Integer type"
        v shouldBe 0.asJson
    }

    inside(decodedVoid) {
      case Sample(name, v) ⇒
        name shouldBe "Sample with Void type"
        v shouldBe Json.Null
    }
    Sample("Sample with different type", true.asJson).asJson.toString should equal(sampleJsonOut)
  }

}
