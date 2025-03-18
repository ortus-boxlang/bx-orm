/**
 * I am a non-discriminated joined subclass
 */
component
	persistent="true"
	entityname="cbCommentSubscription"
	table     ="cb_commentSubscriptions"
	extends   ="BaseSubscription"
	joinColumn="subscriptionID"
	cachename ="cbCommentSubscription"
	cacheuse  ="read-write"
{
	property
		name     ="relatedContent"
		notnull  ="true"
		cfc      ="root.models.cms.BaseContent"
		fieldtype="many-to-one"
		fkcolumn ="FK_contentID"
		lazy     ="true"
		orderBy  ="Title ASC";

}
