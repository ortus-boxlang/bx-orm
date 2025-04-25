component
	persistent="true"
	table="compositePlayingFields"
	schema="dbo"
	output="false"
	cachename="Games"
	cacheuse="nonstrict-read-write"
{

	property name="fieldID" column="fieldID" type="numeric" ormtype="integer" fieldtype="id" generator="assigned";
	property name="userID" column="userID" type="string" ormtype="string" fieldtype="id" generator="assigned";
}