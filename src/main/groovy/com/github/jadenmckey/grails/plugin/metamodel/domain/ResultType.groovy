package com.github.jadenmckey.grails.plugin.metamodel.domain

/**
 * Bean covering the properties of a standard result as returned
 * from functions executed by the meta model plugin.
 */
class ResultType {

	/** The status code of the result. */
	Integer status
	
	/** The level of the result. */
	ResultLevelType level

	/** The message string of the result. */
	String message
}
