package com.github.jadenmckey.grails.plugin.metamodel.service

import java.text.DateFormat
import java.text.SimpleDateFormat

import org.grails.core.DefaultGrailsDomainClass
import org.grails.core.DefaultGrailsDomainClassProperty
import org.springframework.beans.factory.InitializingBean
import org.springframework.dao.DataIntegrityViolationException

import com.github.jadenmckey.grails.plugin.metamodel.domain.GrailsField
import com.github.jadenmckey.grails.plugin.metamodel.domain.GrailsTable
import com.github.jadenmckey.grails.plugin.metamodel.domain.RecordModificationType
import com.github.jadenmckey.grails.plugin.metamodel.domain.ResultLevelType
import com.github.jadenmckey.grails.plugin.metamodel.domain.ResultType
import com.github.jadenmckey.grails.plugin.metamodel.exception.InvalidDataException
import com.github.jadenmckey.grails.plugin.metamodel.exception.InvalidParametersException

import grails.core.GrailsApplication
import grails.core.GrailsDomainClass
import grails.core.GrailsDomainClassProperty
import grails.validation.ConstrainedProperty
import grails.validation.Constraint

/**
 * Provides generic methods for retrieval of meta data.
 */
class MetaModelService implements InitializingBean {

	/** Although this is the default, we explicitly set scope singleton to prevent future mistakes. */
	static scope = "singleton"

	/** Standard format of dates for uploads and downloads */
	static final DateFormat DATE_FORMAT = new SimpleDateFormat("YYYY-MM-DD HH:mm:ss.S")

	/** The Grails application (auto-wired). */
	GrailsApplication grailsApplication

	/** The Grails table store. */
	Map<String,GrailsTable> tableStore = [:]

	/**
	 * Spring hook to create table store after service instantiation.
	 */
	void afterPropertiesSet() throws Exception {
		// Retrieve or initialize exclude list
		List<String> excludeList = (grailsApplication.config.grails.plugins.metamodel.domainClasses.excluded) ?: []

		// Add meta model data of all domain classes to the store
		grailsApplication.domainClasses.each { GrailsDomainClass grailsDomainClass ->
			if (!(grailsDomainClass.name in excludeList)) {
				getTable(grailsDomainClass)
			}
		}
	}

	/**
	 * Constructs the domain class from the provided domain class name.
	 *
	 * @param The fully qualfied package name of the domain class.
	 * @return The domain class.
	 * @throws InvalidParametersException If provided class name not found in classpath.
	 */
	Class getDomainClass(String domainClassName) throws InvalidParametersException {

		// Construct and initialize Grails domain class
		Class domainClass
		try {
			// Retrieve class
			domainClass = Class.forName(domainClassName, true, Thread.currentThread().contextClassLoader)

		} catch (ClassNotFoundException ex) {
			throw new InvalidParametersException("Class '${domainClassName}' does not exist in classpath")
		}

		// Return to caller with domain class
		return domainClass
	}

	/**
	 * Retrieves an existing cached entry or creates
	 * a new one for the requested domain class.
	 *
	 * @param domainClassName The fully qualified package name of the domain class.
	 * @return The master data as a conceptual table.
	 */
	GrailsTable getTable(String domainClassName) {

		// Entry exists in store (but may be null) ?
		if (!tableStore.containsKey(domainClassName)) {
			// Retrieve domain class
			Class domainClass = getDomainClass(domainClassName)

			// Retrieve Grails domain class
			GrailsDomainClass grailsDomainClass = new DefaultGrailsDomainClass(domainClass)

			// Construct new Grails table
			GrailsTable table = new GrailsTable(grailsDomainClass: grailsDomainClass)

			// Add this entry BEFORE recursive execution.
			// This way the store is also used as a "recursive tracker" in
			// which we are able to detect that a table is already being
			// inspected on a higher level in the recursive hierarchy.
			tableStore[domainClassName] = table

			// Retrieve meta data of this domain class
			// REMARK: This may initiate recursive inspection of foreign keys
			setFields(table)
		}

		// Return to caller with the cached Grails table entry
		return tableStore[domainClassName]
	}

	/**
	 * Wraps {@link getOrCreate(String)}
	 *
	 * @param domainClass The domain class.
	 * @return The master data as a conceptual table.
	 */
	GrailsTable getTable(Class domainClass) {
		// Delegate to base method
		return getTable(domainClass.name)
	}

	/**
	 * Wraps {@link getOrCreate(String)}
	 *
	 * @param domainClass The Grails domain class.
	 * @return The master data as a conceptual table.
	 */
	GrailsTable getTable(GrailsDomainClass grailsDomainClass) {
		// Delegate to base method
		return getTable(grailsDomainClass.clazz)
	}

	/**
	 * Retrieves the list field names for the requested domain class.
	 *
	 * @param domainClassName The fully qualified package name of the domain class.
	 * @return The list of field names.
	 */
	List<String> getFieldNames(String domainClassName) {
		return getTable(domainClassName).fields.collect{ it.name } as String[]
	}

	/**
	 * Retrieves the most relevant meta data by inspecting the
	 * Grails domain class and properties of the provided class.
	 * The meta data is translated into the terminology of a
	 * traditional "table" with "fields".
	 *
	 * @param table The Grails table yet without fields.
	 * @return The master data as a conceptual table.
	 * @throws InvalidParametersException If invalid content found in the domain class meta data.
	 */
	private void setFields(GrailsTable table) throws InvalidParametersException {

		// Local variables
		List fields = []

		// Get corresponding Grails domain class
		GrailsDomainClass grailsDomainClass = table.grailsDomainClass

		// Retrieve list of property names making up the unique constraint
		List<GrailsDomainClassProperty> uniquePropertyList = getPrimaryKeys(grailsDomainClass)

		// Process all properties having constraints defined
		grailsDomainClass.constrainedProperties.each { String propertyName, ConstrainedProperty constrainedProperty ->
			// Retrieve the meta data of the current property
			GrailsDomainClassProperty grailsDomainClassProperty = grailsDomainClass.propertyMap[propertyName]

			if (!grailsDomainClassProperty.oneToMany) {
				// Store data for this field
				GrailsField field = new GrailsField(
						name       :  propertyName,
						type       :  grailsDomainClassProperty.type,
						property   :  grailsDomainClassProperty,
						constraints:  constrainedProperty,
						primaryKey :  uniquePropertyList.find{ it.name == propertyName } ? true : false,
						foreignKey : (grailsDomainClassProperty.association && !grailsDomainClassProperty.oneToMany) ? true : false,
						required   :  grailsDomainClassProperty.isOptional() ? false : true)

				// Retrieve master data of table referenced by this foreign key
				if (field.foreignKey) field.foreignTable = getTable(grailsDomainClassProperty.type)

				// Add current field to fields list
				fields << field
			}
		}

		// Store fields for this table
		table.fields = fields

		// Create bi-directional link from fields back to table
		table.fields.each{ it.table = table }
	}

	/**
	 * Retrieves the list of Grails domain class properties satisfying the
	 * logical uniqueness constraint of the provided Grails domain class.
	 *
	 * @param grailsDomainClass The grails domain class.
	 * @return The list of Grails domain class properties.
	 * @throws InvalidParametersException If no or invalid unique constraint found.
	 */
	List<DefaultGrailsDomainClassProperty> getPrimaryKeys(DefaultGrailsDomainClass grailsDomainClass) throws InvalidParametersException {

		// Local variables
		int count = 0
		List<String> propertyNames = []

		// Process all properties having constraints defined
		grailsDomainClass.constrainedProperties.each { String propertyName, ConstrainedProperty constrainedProperty ->

			// Get unique constraint on current property
			Constraint uniqueConstraint = constrainedProperty.getAppliedConstraint("unique")

			// Unique constraint defined indeed ?
			if (uniqueConstraint) {
				// Evaluate parameter of this unique constraint
				def parameter = uniqueConstraint.getParameter()
				switch (parameter) {
					// Format: field1(unique: true)"
					case Boolean:
						propertyNames << propertyName
						break

					// Format: field2(unique: "field1")
					case String:
						propertyNames += parameter
						propertyNames << propertyName
						break

					// Format: field3(unique: ["field1", "field2"])
					case List:
						propertyNames += parameter
						propertyNames << propertyName
						break

					// Unsupported
					default:
						throw new InvalidParametersException("Grails domain class '" + grailsDomainClass + "' contains unsupported unique constraint")
				}

				// Next unique constraint
				count++
			}
		}

		// Fail if no "unique" properties found
		if (propertyNames.size() == 0) throw new InvalidParametersException("Grails domain class '" + grailsDomainClass + "' contains no unique constraint")

		// Fail if more than 1 unique constraint found
		if (count > 1) throw new InvalidParametersException("Grails domain class '" + grailsDomainClass + "' contains multiple unique constraint")

		// Retrieve the Grails domain class properties corresponding to the names of the "unique" properties
		List<DefaultGrailsDomainClassProperty> result = grailsDomainClass.constrainedProperties.findAll{ propertyNames.contains(it.key) }.collect{ grailsDomainClass.propertyMap[it.key] }

		// Return to caller with list of properties making up the unique constraint
		return result
	}

	/**
	 * Filters the primary key fields and corresponding values from the provided values map.
	 *
	 * @param table The Grails table.
	 * @param values The values of the instance properties.
	 * @return The primary key values map.
	 */
	Map<String,Object> getPrimaryKeyValues(GrailsTable table, Map<String,Object> values) {

		// Construct list of primary key fields
		List<GrailsField> primaryKeyFields = table.fields.findAll{ it.primaryKey }

		// Construct map of primary key values
		Map<String,Object> primaryKeyValues = primaryKeyFields.collectEntries{ [(it.name): values[it.name]] }

		// Return to caller with primary key value map
		return primaryKeyValues
	}

	/**
	 * Filters the primary key fields and corresponding values from the provided values map.
	 *
	 * @param domainClassName The fully qualified package name of the domain class.
	 * @param values The values of the instance properties.
	 * @return The primary key values map.
	 */
	Map<String,Object> getPrimaryKeyValues(String domainClassName, Map<String,Object> values) {

		// Retrieve Grails table object corresponding to provided full qualified domain class name
		GrailsTable table = this.getTable(domainClassName)

		// Delegate to base method
		return this.getPrimaryKeyValues(table, values)
	}

	/**
	 * Searches the domain class instance corresponding to
	 * the provided (combination of) primary key values. In the
	 * values map at least the primary key values have to be
	 * provided at a minimum.
	 *
	 * @param table The Grails table.
	 * @param values The values of the instance properties.
	 * @return The domain class instance of null of not found.
	 */
	Object getRecord(GrailsTable table, Map<String,Object> values) {

		// Construct lists of primary key fields and values
		List<GrailsField> primaryKeyFields = table.fields.findAll{ it.primaryKey }
		List<Object> primaryKeyValues = primaryKeyFields.collect{ values[it.name] }

		// Construct query
		String whereClause = primaryKeyFields.collect{ "table." + it.name + " = ?" }.join(" and ")
		String query = "from ${table.shortName} as table where ${whereClause}"

		// Find domain class instance by executing query
		def domainClassInstance = table.domainClass.find(query, primaryKeyValues)

		// Return to caller with domain class instance found (or null of not found)
		return domainClassInstance
	}

//	/**
//	 * Hierarchically inspect the "unique" properties of the Grails domain class referenced
//	 * by the provided property. In case one of the "unique" properties of the referenced domain
//	 * class on its turn references another domain class this one is inspected also. The result is
//	 * a flat list of properties that each do not further reference other domains.
//	 *
//	 * @param grailsDomainClassProperty The Grails domain class property.
//	 * @param grailsDomainClassPropertyList The list tracking the flat list of referenced "unique" properties.
//	 * @return The list tracking the flat list of referenced "unique" properties.
//	 */
//	List<DefaultGrailsDomainClassProperty> getForeignPrimaryKeys(DefaultGrailsDomainClassProperty grailsDomainClassProperty, List<DefaultGrailsDomainClassProperty> grailsDomainClassPropertyList) {
//
//		// Construct Grails domain class of the referenced type
//		DefaultGrailsDomainClass grailsDomainClass = new DefaultGrailsDomainClass(grailsDomainClassProperty.type)
//
//		// Retrieve list of properties making up the unique constraint of the referenced Grails domain class
//		List<DefaultGrailsDomainClassProperty> referencedUniquePropertyList = getPrimaryKeys(grailsDomainClass)
//
//		// Process all "unique" properties
//		referencedUniquePropertyList.each { DefaultGrailsDomainClassProperty referencedGrailsDomainClassProperty ->
//			// Current referenced property on its turn references another domain class ?
//			if (referencedGrailsDomainClassProperty.association && !referencedGrailsDomainClassProperty.oneToMany) {
//				// Further hierarchical inspection up the reference chain
//				getForeignPrimaryKeys(referencedGrailsDomainClassProperty, grailsDomainClassPropertyList)
//
//			} else {
//				// Add current property to the flat list
//				grailsDomainClassPropertyList << referencedGrailsDomainClassProperty
//			}
//		}
//
//		// Return to caller with resulting property list
//		return grailsDomainClassPropertyList
//	}

	/**
	 * Retrieves the sequence of primary keys recursively referred to by the provided foreign key field.
	 *
	 * @param foreignKeyField The foreign key field to inspect recursively.
	 * @param foreignKeyFieldPath The path of foreign keys collected up till current level of recursion (optional).
	 * @param level The recursion depth (optional).
	 * @return The foreign key path.
	 */
	List<GrailsField> getForeignKeyPath(GrailsField foreignKeyField, List<GrailsField> foreignKeyPath = [], int level = 0) throws InvalidDataException {

		// Prevent looping by safely breaking out of extreme deep recursion
		if (level >= 100) throw new InvalidDataException("Breaking out of recursion at level 100")

		// Process all primary key fields of the foreign table
		foreignKeyField.foreignTable.fields.findAll{ it.primaryKey }.each { GrailsField field ->

			// Add current field to path
			foreignKeyPath << field

			// Current primary key field is a foreign key ?
			if (field.foreignKey) {
				// Delegate further downstream retrieval
				getForeignKeyPath(field, foreignKeyPath, level + 1)
			}
		}

		// Return to caller with the foreign key path
		return foreignKeyPath
	}

	/**
	 * Creates a unique ordered list of tables that are involved in the
	 * provided foreign key path. This list can be used in HQL joins.
	 *
	 * @param foreignKeyField The foreign key field.
	 * @param foreignKeyPath The foreign key path.
	 * @return The foreign table path.
	 */
	List<GrailsTable> getForeignTablePath(GrailsField foreignKeyField, List<GrailsField> foreignKeyPath) {

		// Initialize the foreign table path with the first foreign table
		List<GrailsTable> foreignTablePath = [foreignKeyField.foreignTable]

		// Add all other foreign tables only once to the foreign table path
		foreignKeyPath.each{ if (!foreignTablePath.contains(it.table)) foreignTablePath << it.table }

		// Return to caller with the foreign table path
		return foreignTablePath
	}

	/**
	 * Retrieves the string representation of the provided foreign domain class instance.
	 * This is done by concatenating all hierarchical non-foreign key values.
	 *
	 * @param field The Grails field.
	 * @param foreignDomainClassInstance The foreign domain class instance.
	 * @param separator The separator character (optional).
	 * @return The string representation of the provided foreign domain class instance.
	 * @throws InvalidParametersException If provided Grails field is not a foreign key.
	 * @throws InvalidDataException If multiple instances of the foreign record are found.
	 */
	String getForeignRecordAsString(GrailsField field, Object foreignDomainClassInstance, char separator = '|') throws InvalidParametersException, InvalidDataException {

		// Fail if provided field is not a foreign key
		if (!field.foreignKey) throw new InvalidParametersException("The provided field '${field.name}' is no foreign key")

		// Retrieve foreign key path
		List<GrailsField> foreignKeyPath = getForeignKeyPath(field)

		// Retrieve foreign table join path
		List<GrailsTable> foreignTablePath = getForeignTablePath(field, foreignKeyPath)

		// Construct HQL query
//		CAUSES EXCEPTION: Conversion failed when converting the varchar value 'CPA|EMEA|Nuremberg|DMZ Zone 1|IPv4|' to data type int
//		String selectClause = "concat(" + foreignKeyPath.findAll{ !it.foreignKey }.collect{ it.table.shortName.toCamelCase() + "." + it.name }.join(", '${separator}', ") + ") as foreignKeyString"
		String selectClause =  foreignKeyPath.findAll{ !it.foreignKey }.collect{ it.table.shortName.toCamelCase() + "." + it.name }.join(", ")
		String fromClause   =  foreignTablePath.collect{ it.shortName + " as " + it.shortName.toCamelCase() }.join(", ")
		String whereClause  =  foreignKeyPath.findAll{  it.foreignKey }.collect{ it.table.shortName.toCamelCase() + "." + it.name + " = " + it.foreignTable.shortName.toCamelCase() }.join(" and ")
		whereClause        += (whereClause ? " and " : "") + field.foreignTable.shortName.toCamelCase() + " = ?"
		String query = "select ${selectClause} from ${fromClause} where ${whereClause}"

		// Execute HQL query
		def foreignRecordList = field.foreignTable.domainClass.executeQuery(query, foreignDomainClassInstance)

		// Found more than 1 record ?
		// REMARK: Should never happen !
		if (foreignRecordList.size() > 1) throw new InvalidDataException("The value '" + foreignDomainClassInstance + "' is found multiple times in the database")

		// Construct foreign record as string by concatenating key field values with separators
		String foreignRecordAsString = (foreignRecordList[0] instanceof String) ? foreignRecordList[0] : foreignRecordList[0]*.toString().join("|")

		// Return to caller with foreign record as string
		return foreignRecordAsString
	}

	/**
	 * Retrieves the foreign domain class instance corresponding to the provided string
	 * representation of the primary key values. This string representation has to
	 * concatenate all hierarchical non-foreign key values.
	 *
	 * @param field The Grails field.
	 * @param foreignKeyValueString The string representation of the foreign domain class instance.
	 * @param separator The separator character (optional).
	 * @return The foreign domain class instance.
	 * @throws InvalidParametersException If provided Grails field is not a foreign key.
	 * @throws InvalidDataException If multiple instances of the foreign record are found.
	 */
	Object getForeignRecordFromString(GrailsField field, String foreignKeyValueString, char separator = '|') throws InvalidParametersException, InvalidDataException {

		// Fail if provided field is not a foreign key
		if (!field.foreignKey) throw new InvalidParametersException("The provided field '${field.name}' is no foreign key")

		// Retrieve foreign key path
		List<GrailsField> foreignKeyPath = getForeignKeyPath(field)

		// Filter the fields that are leafs in the foreign key path
		List<GrailsField> foreignKeyLeafs = foreignKeyPath.findAll{ !it.foreignKey }

		// Split provided foreign key value into its element as list of strings
		// See also: http://jermdemo.blogspot.nl/2009/07/beware-groovy-split-and-tokenize-dont.html
		List<String> foreignKeyStringValueList = (foreignKeyValueString + separator + "dummy").split(/\${separator}/).dropRight(1) as List<String>
		
		// Fail if the amount of values does not match the expected foreign key path size (leafs only)
		if (foreignKeyStringValueList.size() != foreignKeyLeafs.size()) throw new InvalidDataException("The value '" + foreignKeyStringValueList.join("|") + "' of field '${field.name}' should contain ${foreignKeyLeafs.size()} elements")

		// Construct list of the foreign key values (leafs only) matching the corresponding field type 
		List<Object> foreignKeyValueList = []
		int i = 0
		foreignKeyLeafs.each { GrailsField foreignField ->
			if (foreignKeyStringValueList[i]) {
//println("JMCK DEBUG 1: " + foreignField.name + "(" + foreignField.type + ") = " + foreignKeyStringValueList[i])
//				if (foreignField.type == Date) {
//					foreignKeyValueList << DATE_FORMAT.parse(foreignKeyStringValueList[i]) 
//				} else {
					foreignKeyValueList << foreignField.type.newInstance(foreignKeyStringValueList[i])
//				}
//println("JMCK DEBUG 2: " + foreignKeyValueList.takeRight(1))
			} else {
				foreignKeyValueList << null
			}
			i++
		}

		// Retrieve foreign table join path
		List<GrailsTable> foreignTablePath = getForeignTablePath(field, foreignKeyPath)

		// Construct HQL query
		String fromClause  = foreignTablePath.collect{ it.shortName + " as " + it.shortName.toCamelCase() }.join(", ")
		String whereClause = foreignKeyPath.collect{
			if (it.foreignKey) {
				it.table.shortName.toCamelCase() + "." + it.name + " = " + it.foreignTable.shortName.toCamelCase()
			} else {
				it.table.shortName.toCamelCase() + "." + it.name + " = ?"
			}
		}.join(" and ")
		String query = "select " + field.foreignTable.shortName.toCamelCase() + " from ${fromClause} where ${whereClause}"

		// Execute HQL query
		def foreignRecordList = field.foreignTable.domainClass.executeQuery(query, foreignKeyValueList)

		// Found more than 1 record ?
		// REMARK: Should never happen !
		if (foreignRecordList.size() > 1) throw new InvalidDataException("The value '" + foreignKeyStringValueList.join("|") + "' is found multiple times in the database")

		// Return to caller with foreign record
		return foreignRecordList[0]
	}

	/**
	 * Constructs a map in which:
	 * <ol><li>The default String type (from CSV input) is converted into the required field type</li>
	 * <li>The foreign keys of the input row are translated into their domain class instance</li></ol>
	 * In case a foreign record is not found, the method throws an exception.
	 *
	 * @param table The meta data of the table.
	 * @param record The input record.
	 * @return Flag indicating whether all foreign key values are present.
	 * @throws InvalidDataException If referenced record not found.
	 */
	Map<String,Object> convertTypes(GrailsTable table, Map<String,String> record) throws InvalidDataException {

		// Assume record is valid until encountered otherwise
		boolean recordValid = true

		// Initialize list of errors
		List<String> errorList = []

		// Convert all fields in provided record
		Map<String,Object> recordConverted = record.collectEntries{ key, value ->
			// Initialize converted value for current field
			// REMARK: At failure the value remains "null"
			def valueConverted = null

			// Get current field
			GrailsField field = table.index[key]

			// Value is defined and not empty ?
			if (value && !(value =~ /^\s*$/)) {
				// Current field is a foreign key ?
				if (field.foreignKey) {
					// Retrieve referenced record
					def rowDbForeign = getForeignRecordFromString(field, value)

					// Referenced record exists ?
					if (rowDbForeign) {
						// Store converted value
						valueConverted = rowDbForeign

					} else {
						// Mark record as invalid
						recordValid = false

						// Add error to list
						errorList << "Record '${value}' does not exist in table '${field.foreignTable.shortName}'"
					}
				} else {
					// Convert provided string value into the target class type
					// REMARK: Most native types provide a constructor that creates
					//         instances based on their string representation.
					valueConverted = field.type.newInstance(value)
				}
			} else {
				// A boolean field value being empty in CSVs corresponds to false
				if (field.type == Boolean) {
					// So lets initialize the converted value explicitly
					valueConverted = new Boolean(false)
				}
			}

			// Add converted value to converted record
			[(key): valueConverted]
		}

		// Throw exception if record is invalid
		if (!recordValid) throw new InvalidDataException("MISSING REFERENCES: [" + errorList.join("; ") + "]")

		// Return to caller with converted record
		return recordConverted
	}

	/**
	 * Checks whether all required values are provided in
	 * the requested fields of the provided input record and
	 * returns the list of fields that fail validation.
	 *
	 * @param fields The list of Grails fields to check.
	 * @param record The input record.
	 * @return The list of required fields that have no value.
	 */
	List<GrailsField> getRequiredFieldsWithoutValue(List<GrailsField> fields, Map<String,Object> record) {

		// Initialize the list of required fields that have no value
		List<GrailsField> fieldList = []

		// Inspect the values of all required fields
		fields.findAll{ it.required }.each { GrailsField field ->
			// No value for current field ?
			if (record[field.name] == null) {
				// Add current field to list of required fields that have no value
				fieldList << field
			}
		}

		// Return to caller with the list of required fields that have no value
		return fieldList
	}

	/**
	 * Returns the property values of the provided domain class instance as a map.
	 *
	 * @param table The Grails table.
	 * @param domainClassInstance The domain class instance.
	 * @return THe property value map.
	 */
	Map<String,Object> domainClassInstanceToMap(GrailsTable table, Object domainClassInstance) {
		return table.fields.collectEntries{ def value = domainClassInstance."${it.name}"; [(it.name): value] }
	}

	/**
	 * Collects the changed Grails fields in the PMO and FMO record map.
	 *
	 * @param table The Grails table.
	 * @param recordCurrent The current record.
	 * @param recordNew The new record.
	 * @return The changed list of Grails fields.
	 */
	List<GrailsField> getChangedFields(GrailsTable table, Map<String,Object> recordCurrent, Map<String,Object> recordNew) {
		return recordNew.findAll{ key, value ->
			table.index[key].foreignKey ? value?.id != recordCurrent."${key}"?.id : value != recordCurrent."${key}"
		}.collect{ table.index[it.key] }
	}

	/**
	 * Executes the provided closure against the values of all entities
	 * as stored in the instances of the requested domain class.
	 *
	 * @param domainClassName The fully qualified package name of the domain class.
	 */
	void processAllEntities(String domainClassName, Closure closure) {
		// Retrieve Grails table object corresponding to provided full qualified domain class name
		GrailsTable table = this.getTable(domainClassName)

		// Process all domain class object instances
		table.domainClass.findAll().each { def domainClassInstance ->
			// Construct array containing the property values of the current instance
			String[] propertyValueArray = table.fields.collect{ GrailsField field ->
				// Get value of current property
				def value = domainClassInstance."${field.name}"

				// Convert current value to string
				String stringValue = null
				if (value) {
					if (field.foreignKey) {
						stringValue = this.getForeignRecordAsString(field, value)
					} else {
//						if (field.type == Date) {
//							stringValue = new SimpleDateFormat(DATE_FORMAT).format(value)
//						} else {
							stringValue = value as String
//						}
					}
				}

				// Return current string value from closure
				stringValue
			}

			// Execute provided closure against array
			closure(propertyValueArray)
		}
	}

	/**
	 * Inserts, updates or deletes the provided record according the specified modification
	 * type. The record is provided as a map of strings in which key is the column name.  
	 * 
	 * @param domainClassName The fully qualified package name of the domain class.
	 * @param rowInput The input row as a map of strings.
	 * @param recordModificationType The type of modification to apply to this record.
	 * @return The result of the modification.
	 */
	ResultType modifyRecord(String domainClassNameFqpn, Map<String,String> rowInput, RecordModificationType recordModificationType) {

		// Initialize result
		ResultType result = null

		// Retrieve meta data of this domain class
		GrailsTable table = this.getTable(domainClassNameFqpn)

		// Retrieve header containing column names
		List<String> header = rowInput.collect{ it.key }

		// Verify that column names match the field names in the master data
		List mismatches = header.findAll{ !table.index[it] }.collect{ it.key }
		if (mismatches.size() > 0) throw new InvalidDataException("Table '${table.name}' does not contain the following fields provided in header: " + mismatches.join(", "))

		Map<String,Object> rowInputConverted
		try {
			// Convert the non-foreign key values into their domain class property type and
			// the foreign key values into their corresponding domain class instance
			rowInputConverted = this.convertTypes(table, rowInput)

		} catch (Exception ex) {
			// Set result
			result = new ResultType(status: 4, level: ResultLevelType.ERROR, message: ex.message)
		}

		// Provided input row only references existing foreign records ?
		if (rowInputConverted) {
			// Check if record already exists
			def domainClassInstance = this.getRecord(table, rowInputConverted)

			// Record already exists ?
			// REMARK: For existing records the columns provided in the header list are leading.
			//         For new records the columns as specified in the master data are leading.
			if (domainClassInstance) {
				// Evaluate action
				switch (recordModificationType) {
					// Update action requested ?
					case [RecordModificationType.UPDATE, RecordModificationType.UPSERT]:
						// Construct map of the current domain class instance property values
						Map<String,Object> rowCurrent = this.domainClassInstanceToMap(table, domainClassInstance)

						// Collect master data for the fields of the changed columns
						// REMARK: For foreign keys we use the id of the domain class instance for comparing
						List<GrailsField> updateList = this.getChangedFields(table, rowCurrent, rowInputConverted)

						// Any changes ?
						if (updateList.size() > 0) {
							// Verify all required values are provided in the set of changed columns
							List<GrailsField> requiredFieldsWithoutValue = this.getRequiredFieldsWithoutValue(updateList, rowInputConverted)

							// Record content is valid ?
							if (requiredFieldsWithoutValue.size() == 0) {
// Log changes
// updateList.each{ log.info("Updating field '${it.name}' from '" + rowCurrent."${it.name}" + "' to '${rowInputConverted[it.name]}'") }

								// Update changed values
								updateList.each{ domainClassInstance."${it.name}" = rowInputConverted[it.name] }

								try {
									// Save instance
									domainClassInstance.save(flush: true)

									// Set result
									result = new ResultType(status: 0, level: ResultLevelType.INFO, message: "Update successful")

								} catch (InvalidDataException ex) {
									// Set result
									result = new ResultType(status: 6, level: ResultLevelType.ERROR, message: "Update failed: " + ex.message)
								}
							} else {
								// Set result
								result = new ResultType(
									status :  5,
									level  :  ResultLevelType.ERROR,
									message: "Record invalid: No required value(s) provided for fields: " + requiredFieldsWithoutValue.collect{ it.name }.join(", "))
							}
						} else {
							// Set result
							result = new ResultType(status: 0, level: ResultLevelType.INFO, message: "No changes")
						}

						// End of switch
						break

					// Delete action requested ?
					case RecordModificationType.DELETE:
						try {
							// Delete record
							domainClassInstance.delete(flush: true)

							// Set result
							result = new ResultType(status: 0, level: ResultLevelType.INFO, message: "Delete successful")

						} catch (DataIntegrityViolationException ex) {
							// Set result
							result = new ResultType(status: 6, level: ResultLevelType.ERROR, message: "Delete failed: " + ex.message)
						}

						// End of switch
						break

					default:
						// Set result
						result = new ResultType(
							status :  2,
							level  :  ResultLevelType.WARN,
							message: "Record exists: Skipped")
				}
			} else {
				// Insert action request ?
				if (recordModificationType in [RecordModificationType.INSERT, RecordModificationType.UPSERT]) {
					// Verify all required values are provided
					List<GrailsField> requiredFieldsWithoutValue = this.getRequiredFieldsWithoutValue(table.fields, rowInputConverted)

					// Record content is valid ?
					if (requiredFieldsWithoutValue.size() == 0) {
						// Create new instance
						def domainClassInstanceNew = table.domainClass.newInstance()
						table.fields.each{ domainClassInstanceNew."${it.name}" = rowInputConverted[it.name] }

						try {
							// Save instance
							domainClassInstanceNew.save(flush: true)

							// Set result
							result = new ResultType(status: 0, level: ResultLevelType.INFO, message: "Insert successful")

						} catch (InvalidDataException ex) {
							// Set result
							result = new ResultType(status: 6, level: ResultLevelType.ERROR, message: "Insert failed: " + ex.message)
						}
					} else {
						// Set result
						result = new ResultType(
							status :  5,
							level  :  ResultLevelType.ERROR,
							message: "Record invalid: No required value(s) provided for fields: " + requiredFieldsWithoutValue.collect{ it.name }.join(", "))
					}
				} else {
					// Set result
					result = new ResultType(
						status :  2,
						level  :  ResultLevelType.WARN,
						message: "Record does not exist: Skipped")
				}
			}
		}

		// Return to caller with result
		return result
	}
}
