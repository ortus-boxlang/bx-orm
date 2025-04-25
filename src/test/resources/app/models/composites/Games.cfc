component persistent="true" table="compositeGames" accessors=true {

	property name="teamGameNum" column="teamGameNum" type="string" ormtype="string" fieldtype="id";
	property name="gameUserID" column="gameUserID" type="string" ormtype="string";
	property name="fieldID" type="numeric" ormtype="integer" column="fieldID";

	property name="field" fieldtype="many-to-one" cfc="PlayingField" fkcolumn="fieldID,gameUserID" type="string" ormtype="string" inverse="true" insert="false" update="false";
}