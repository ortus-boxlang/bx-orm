﻿component
	persistent="true"
	table     ="abstract_categories"
	extends   ="Abstract"
	cachename ="abstract_categories"
	cacheuse  ="read-write"
{

	property
		name     ="catid"
		column   ="category_id"
		fieldType="id"
		generator="uuid";

	property name="category"    notnull="true";

	property name="description" notnull="true";

	property
		name   ="modifydate"
		insert ="false"
		update ="false"
		ormtype="timestamp";

	function init(){
		this.created = now();
	}

}
