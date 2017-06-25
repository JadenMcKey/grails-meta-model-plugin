package com.github.jadenmckey.grails.plugin.metamodel.domain

import grails.core.GrailsDomainClass

/**
 * Bean containing the most relevant meta data of a Grails domain
 * class translated into the terminology of a traditional "table".
 */
class GrailsTable {

	/** The full qualified package name of the table. */
	String name

	/** The short name of the table. */
	String shortName

	/** The domain class corresponding to this table. */
	Class domainClass

	/** The Grails domain class corresponding to this table. */
	GrailsDomainClass grailsDomainClass

	/** The list of fields in this table. */
	List<GrailsField> fields

	/** The supporting index of the fields. */
	Map<String, GrailsField> index

	/**
	 * Sets the name and short name of this Grails table.
	 * 
	 * @param name The name of this table.
	 */
	void setName(String name) {

		// Store provided name
		this.name = name

		// Construct short name
		this.shortName = name.split(/\./).last()
	}

	/**
	 * Sets the Grails domain class, class, name and short name of this Grails table.
	 * 
	 * @param grailsDomainClass The Grails domain class.
	 */
	void setGrailsDomainClass(GrailsDomainClass grailsDomainClass) {

		// Store provided Grails domain class
		this.grailsDomainClass = grailsDomainClass

		// Store corresponding class
		this.domainClass = grailsDomainClass.clazz

		// Set name and short name
		setName(domainClass.name)
	}

	/**
	 * Sets the list of fields and creates the fields index.
	 * 
	 * @param fields The list of fields.
	 */
	void setFields(List<GrailsField> fields) {

		// Store provided list of fields
		this.fields = fields

		// Construct index map referencing each field by its name
		this.index = [:]
		fields.each { GrailsField field -> this.index += [(field.name): field] }
	}
}
