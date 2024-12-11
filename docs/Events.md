# ORM Events

There are several types of event listeners in BoxLang ORM:

- [ORM Events](#orm-events)
  - [Global Event Handler](#global-event-handler)
  - [Entity-Specific Event Listeners](#entity-specific-event-listeners)
  - [BoxLang Interception Points](#boxlang-interception-points)

## Global Event Handler

```js
// Application.bx
this.ormSettings = {
    eventHandling : true,
    eventHandler : "models.GlobalEventHandler"
}
```

Then your event handler will look something like this, implementing any events it wishes:

```js
// models/GlobalEventHandler.bx
class{

    function init(){}

    // these events will ONLY fire upon the global event handler
    function onEvict(){}
    function onDirtyCheck(){}
    function onClear(){}
    function onAutoFlush(){}
    function onFlush(){}

    // These events will fire upon the entity as well
    function preLoad(){}
    function postLoad(){}

    function preInsert(){}
    function postInsert(){}

    function preUpdate(){}
    function postUpdate(){}

    function preDelete(){}
    function postDelete(){}
}
```

## Entity-Specific Event Listeners

BoxLang ORM will fire certain events on the entity itself if the event listener methods are defined in the entity.

```js
//models/orm/MyEntity.bx
class{
    // ORM properties here...

    function init(){}

    // Event listener methods here...
    function preLoad(){}
    function postLoad(){}

    function preInsert(){}
    function postInsert(){}

    function preUpdate(){}
    function postUpdate(){}

    function preDelete(){}
    function postDelete(){}
}
```

## BoxLang Interception Points

Coming soon:

* `on_save`
* `on_evict`
* `on_dirtyCheck`
* `on_clear`
* `on_auto_flush`
* `on_flush`
* `pre_new`
* `post_new`
* `pre_load`
* `post_load`
* `pre_insert`
* `post_insert`
* `pre_update`
* `post_update`