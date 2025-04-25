component persistent="true" table="composites_Rejections" output="false" {

	property name="rejectionID" column="rejectionID" type="string" ormtype="string" fieldtype="id" setter="false" generator="guid";
	property name="eventID" column="eventID" type="string" ormtype="string";
	property name="clientID" column="clientID" type="string" ormtype="string";
	property name="userID" column="userID" type="string" ormtype="string";
	property name="childID" column="childID" type="string" ormtype="string";
	property name="rejectionDate" column="rejectionDate" type="date" ormtype="timestamp";

	property name="user" fieldtype="many-to-one" cfc="root.models.cms.Author" fkcolumn="authorID" insert="false" update="false";

}