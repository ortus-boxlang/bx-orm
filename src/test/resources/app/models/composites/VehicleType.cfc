component entityName="vehicleTypes" persistent="true" {
	property name="make" column="make" type="string" ormtype="string" fieldtype="id" generator="assigned";
	property name="model" column="model" type="string" ormtype="string" fieldtype="id" generator="assigned";
}