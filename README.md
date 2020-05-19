# Graphinity

![graphinity](/img/example.png)

## Run application

`sbt exampleJVM/run`

## Description

There is starting process of initialization during in deploying microservice.</br>
Imagine that several parts of application cannot be initialized for several seconds because:

- `CClient` cannot establish connection with service `CService`.</br>
`CService` in the deployment process and after several seconds it will be accessed(e.g. after crashed);
- At the same time some HTTP - controller accesses the service `BService` across `BClient`.
- But `BClient` is not ready to provide an interface for HTTP - controller due to the unavailability of client `CClient`.

**Graphinity** allow you ability to set relationships between clients and provide functionality to other components of the microservice such as for example HTTP - controllers.

## Short example with using **graphinity** interface:

`BClient` depends on `CClient`.</br>
Until `CClient` readiness - status of `BClient` is *not ready*</br>
And each component of the microservice can check it by using the methods:

- instanceOfBClient.isReady
- allReady

```scala
class BClient extends Vertex {
  ...        
  override val relatesWith = Set(classOf[CClient])
  override def init = ...
  ...
}
```

```scala
class CClient extends Vertex {
  ...        
  override val relatesWith = Set.empty
  override def init = {/*will be ready after 5 seconds*/}
  ...
}
```

#### [see example](https://github.com/korotinm/graphinity/blob/master/example/src/main/scala/graphinity/example/Example.scala)

## Cyclic dependency

If `BClient` and `CClient` depend on each other then an error will occur.

```scala
class BClient extends Vertex {
  ...        
  override val relatesWith = Set(classOf[CClient])
  override def init = ...
  ...
}
```

```scala
class CClient extends Vertex {
  ...        
  override val relatesWith = Set(classOf[BClient])
  override def init = {/*will be ready after 5 seconds*/}
  ...
}
```