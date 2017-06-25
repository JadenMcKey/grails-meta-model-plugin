package com.github.jadenmckey.grails.plugin.metamodel.domain

import grails.core.GrailsDomainClassProperty
import grails.validation.ConstrainedProperty

/**
 * Bean containing the most relevant meta data of a Grails domain class
 * property translated into the terminology of a traditional "table field".
 */
class GrailsField {

	/** The name of the field. */
	String name

	/** The type (class) of the field. */
	Class type

	/** The Grails table to which this field belongs. */
	GrailsTable table

	/** The Grails domain class property object corresponding to this field. */
	GrailsDomainClassProperty property

	/** The constraints defined on this Grails domain class property corresponding to this field. */
	ConstrainedProperty constraints

	/** Flag indicating whether this field is part of the primary key. */
	boolean primaryKey

	/** Flag indicating whether this field (upward) references another table. */
	boolean foreignKey

	/** Flag indicating whether the content of this field may not be empty. */
	boolean required

	/** The table referenced by this field (if foreignKey = true). */
	GrailsTable foreignTable
}
