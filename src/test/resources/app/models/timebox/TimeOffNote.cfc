component persistent="true" table="projectNote" extends="AbstractNote" {

    property
        name="request"
        notnull="true"
        fieldtype="many-to-one"
        cfc="TimeOff"
        fkcolumn="FK_timeOffId"
        lazy="true";

}