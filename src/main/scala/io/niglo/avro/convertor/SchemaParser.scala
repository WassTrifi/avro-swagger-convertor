package io.niglo.avro.convertor

import cats.syntax.either._
import cats.syntax.functor._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.yaml.syntax._
import io.circe.{Decoder, Encoder, Json, JsonObject}
import org.slf4j.LoggerFactory


/** Parser from an Avro Schema Json representation to Yaml
  *
  * @param jsonSchema
  */
private[convertor] class SchemaParser(val jsonSchema: String) {

  import SchemaParser._
  import GenericDerivation._
  import SwaggerAllowedTypes._

  private val logger = LoggerFactory.getLogger(this.getClass.getName)

  logger.info("Starting parsing avro schema json ...")

  /** Structure for  Swagger representation of avro schemas * this is a tree of Schemas (key,Json)*/
  private[convertor] val SwaggerDefinitionTree = collection.mutable.LinkedHashMap.empty[String, Json]

  /** decoded the Avro schema from json format to a specific metaData type [[AvroJsonRecord]] */
  private[convertor] lazy val parseSchema: AvroJsonRecord = decode[AvroJsonRecord](jsonSchema).valueOr {
    failure ⇒
      {
        logger.error(s"Failed to decode the provided Json to an AvroJsonRecord")
        throw ParsingException(failure.getMessage)
      }
  }

  private def orderedTree = SwaggerDefinitionTree.toSeq.reverse.toMap

  /*** Convert Schema as Swagger definition under Yaml representation */
  def toYaml: String = Map[String, Json]("definitions" -> orderedTree.asJson).asJson.asYaml.spaces2

  /**
   * Append SwaggerTree of this instance with other Schema Tree elements
   * @note this operation do not alter the initial object schema
   *       it generates a result of merged tree swagger
   * @param that
   * @return
   */
  def merge(that: SchemaParser): this.type = {
    that.SwaggerDefinitionTree foreach { case (key, value) ⇒ this.SwaggerDefinitionTree.put(key, value) }
    this
  }

  def asObject: this.type = {
    this.asSwaggerObject(parseSchema)
    this
  }

  private[convertor] def asSwaggerObject(avroRecord: AvroJsonRecord): JsonObject = avroRecord match {
    case nested @ AvroComponentUnionObject(_, _, _, _)   ⇒ toSwaggerUnionField(nested)
    case simpleUnion @ AvroSimpleUnionObject(_, _, _, _) ⇒ toSwaggerSimpleUnion(simpleUnion)
    case enum @ AvroEnumObject(_, _, _, _, _)            ⇒ toSwaggerEnumType(enum)
    case json @ AvroJsonObject(_, _, _)                  ⇒ toSwaggerObjectType(json)
    case array @ AvroArrayType(_, _, _, _, _)            ⇒ toSwaggerArrayType(array)
    case simpleField @ AvroSimpleField(_, _, _, _)       ⇒ toSwaggerSimpleField(simpleField)
    case obj @ AvroObject(_, _, _, _, _)                 ⇒ toSwaggerRecord(obj)
  }

  private def toSwaggerRecord(json: AvroObject): JsonObject = {
    logger.debug(s"Processing an Object Record type as : {}", json.`type`)
    val objectName = json.name
    json.`type` match {
      case "record" ⇒
        logger.debug(s"fields process in progress ...")
        val objectFields: Json = json.fields.map { mayBeRecord ⇒ asSwaggerObject(mayBeRecord.get).asJson }.reverse.reduce(_ deepMerge _)
        val objectBody = SchemaSwaggerDefinition(description = json.doc.getOrElse("null"), properties = objectFields).asJson
        JsonObject.fromIterable(SwaggerDefinitionTree.+=((objectName, objectBody)))
      case _ ⇒
        logger.error("Wrong avro record type {}, no handled yet ! ", json.`type`)
        Map[String, Json]("" -> "".asJson).asJsonObject
    }
  }

  private def toSwaggerSimpleField(json: AvroSimpleField): JsonObject = {
    logger.debug("Processing a simple Field as : {} .", json.`type`)
    val fieldName = json.name
    val fieldType = json.`type`
    val fieldBody = FieldSwaggerDefinition(getType(fieldType), json.doc.getOrElse("null")).asJson
    Map[String, Json](fieldName -> fieldBody).asJsonObject
  }

  private def toSwaggerSimpleUnion(json: AvroSimpleUnionObject): JsonObject = {
    logger.debug("Processing a simple Union Type as : {} .", json.`type`)
    val fieldName = json.name
    val fieldType = json.`type`.filterNot(_.equals("null")).head
    val fieldBody = FieldSwaggerDefinition(getType(fieldType), json.doc.getOrElse("null")).asJson
    Map[String, Json](fieldName -> fieldBody).asJsonObject
  }

  private def toSwaggerUnionField(json: AvroComponentUnionObject): JsonObject = {
    logger.debug("Processing a Union Type  as : {}", json.`type`)
    val nestedFieldName = json.name
    val recordAsJson: Json = json.`type`.filter(_.isObject).head
    val recordType: AvroJsonRecord = recordAsJson.as[AvroJsonRecord].valueOr(throw _)
    val fieldBody = recordType match {
      case record @ AvroObject(_, _, _, _, _) ⇒ {
        asSwaggerObject(recordType)
        Map("$ref" -> s"#/definitions/${record.name}").asJson
      }
      case AvroEnumObject(_, _, _, _, _) ⇒
        asSwaggerObject(recordType).asJson
      case _ ⇒ asSwaggerObject(recordType).asJson
    }
    Map[String, Json](nestedFieldName -> fieldBody.asJson).asJsonObject
  }

  private def toSwaggerEnumType(json: AvroEnumObject): JsonObject = {
    logger.debug("We are processing an Enum Type  as : {}", json.`type`)
    val fieldName = json.name
    val fieldType = json.`type`
    SwaggerEnum(enum = json.symbols, default = json.default).asJsonObject
  }

  private def toSwaggerObjectType(json: AvroJsonObject): JsonObject = {
    logger.debug("We are processing an Object Type  as : {}", json.`type`)
    val fieldTypeName = json.name
    val fieldBody = json.`type` match {
      case record @ AvroObject(_, _, _, _, _) ⇒ {
        asSwaggerObject(json.`type`)
        Map("$ref" -> s"#/definitions/${record.name}").asJson
      }
      case AvroEnumObject(_, _, _, _, _) ⇒
        asSwaggerObject(json.`type`).asJson

      case _ ⇒ asSwaggerObject(json.`type`).asJson
    }
    Map[String, Json](fieldTypeName -> fieldBody).asJsonObject
  }

  private def toSwaggerArrayType(json: AvroArrayType): JsonObject = {
    logger.debug(s"We are processing an Array type as : {}", json.`type`)
    val target = json.items.asJson.findAllByKey("name").head.asString.get
    asSwaggerObject(json.items)
    SwaggerArrayField(items = Map("$ref" -> s"#/definitions/$target")).asJsonObject
  }

}

private[convertor] object SchemaParser {
  /** Data Types of all possible Avro Records */
  sealed trait AvroJsonRecord
  final case class AvroObject(`type`: String = "record", name: String, default: Option[Json], doc: Option[String], fields: Array[Option[AvroJsonRecord]]) extends AvroJsonRecord
  final case class AvroSimpleField(`type`: String, name: String, default: Option[Json], doc: Option[String]) extends AvroJsonRecord
  final case class AvroSimpleUnionObject(`type`: Array[String], name: String, default: Option[Json], doc: Option[String]) extends AvroJsonRecord // this is Union type with String
  final case class AvroComponentUnionObject(`type`: Array[Json], name: String, default: Option[Json], doc: Option[String]) extends AvroJsonRecord // this is Union Type with Array
  final case class AvroJsonObject(`type`: AvroJsonRecord, name: String, default: Option[Json]) extends AvroJsonRecord // this is Json Type (object)
  final case class AvroEnumObject(`type`: String = "enum", name: String, symbols: Array[String], doc: Option[String], default: Option[Json]) extends AvroJsonRecord // this is Json Type (object)
  final case class AvroArrayType(`type`: String = "array", name: Option[String], items: AvroJsonRecord, default: Option[Json], doc: Option[String]) extends AvroJsonRecord //Array Items Type

  /** Implicit conversion of AvroSchema to/from Json */
  object GenericDerivation {

    implicit val encodeJsonRecord: Encoder[AvroJsonRecord] = Encoder.instance {
      case nested @ AvroComponentUnionObject(_, _, _, _)   ⇒ nested.asJson
      case simpleUnion @ AvroSimpleUnionObject(_, _, _, _) ⇒ simpleUnion.asJson
      case enum @ AvroEnumObject(_, _, _, _, _)            ⇒ enum.asJson
      case json @ AvroJsonObject(_, _, _)                  ⇒ json.asJson
      case array @ AvroArrayType(_, _, _, _, _)            ⇒ array.asJson
      case simpleField @ AvroSimpleField(_, _, _, _)       ⇒ simpleField.asJson
      case obj @ AvroObject(_, _, _, _, _)                 ⇒ obj.asJson
    }

    implicit val decodeJsonRecord: Decoder[AvroJsonRecord] =
      List[Decoder[AvroJsonRecord]](
        Decoder[AvroObject].widen,
        Decoder[AvroSimpleUnionObject].widen,
        Decoder[AvroComponentUnionObject].widen,
        Decoder[AvroEnumObject].widen,
        Decoder[AvroArrayType].widen,
        Decoder[AvroJsonObject].widen,
        Decoder[AvroSimpleField].widen
      ).reduceLeft(_ or _)
  }

  /** Mapping between avro types and destination swagger allowed types */
  object SwaggerAllowedTypes {

    val AvroSwaggerTypes: Map[String, String] =
      Map("int" -> "integer",
        "double" -> "number",
        "long" -> "integer",
        "boolean" -> "boolean",
        "null" -> "null",
        "string" -> "string")

    def getType(field: String): String = {
      AvroSwaggerTypes.getOrElse(field, field)
    }
  }

  /** Swagger Objects representation as MetaData */
  case class SwaggerArrayField(`type`: String = "array", items: Map[String, String])
  case class SwaggerEnum(`type`: String = "string", enum: Array[String], default: Option[Json])
  case class FieldSwaggerDefinition(`type`: String, description: String)
  case class SchemaSwaggerDefinition(`type`: String = "object", description: String, properties: Json)

  /** Error Parsing */
  case class ParsingException(msg: String) extends Exception(msg)



}

