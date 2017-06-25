package meta.model.plugin

class BootStrap {

    def init = { servletContext ->
		// Add method to convert string into camel case
		String.metaClass.toCamelCase = { ->
			delegate[0].toLowerCase() + delegate.substring(1)
		}
    }
    def destroy = {
    }
}
