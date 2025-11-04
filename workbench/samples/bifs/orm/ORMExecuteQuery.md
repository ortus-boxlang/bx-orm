### Simple HQL Query Execution

The `ORMExecuteQuery` function allows you to execute HQL (Hibernate Query Language) queries against your ORM entities. This function is useful for retrieving data based on specific criteria defined in your HQL query.

```java
var allToyotas = ORMExecuteQuery(
    hql = "FROM Auto WHERE Make = :make",
    params = { make = "Toyota" },
    options = { unique = false }
);
```

## Passing parameters

You can pass parameters to your HQL query using either named parameters (as a struct) or positional parameters (as an array).

```java
var allToyotas = ORMExecuteQuery(
    hql = "FROM Auto WHERE Make = ?",
    params = [ "Toyota" ]
);
```

Using named parameters:

```java
var toyota = ORMExecuteQuery(
    hql = "FROM Auto WHERE Make = :make",
    params = { make = "Toyota" }
);
```

Named parameters are converted to JPA-style positional placeholders in the HQL string prior to execution. Thus, the above query is rewritten as:

```sql
FROM Auto WHERE Make = ?1
```

prior to query execution.