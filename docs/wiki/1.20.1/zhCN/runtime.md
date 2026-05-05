# Serializer Runtime

serializer runtime 负责把 Java 类型和具体读写逻辑连起来。平时使用内置 serializer 时，安装默认 runtime 即可：

```java
PiSerializeRuntime runtime = new PiSerializeRuntime();
PiBuiltInSerializers.install(runtime);
PiSerializeServices.install(runtime);
```

需要在某段代码里临时换一套 serializer，可用 scoped runtime：

```java
PiSerializeServices.withScope(runtime, () -> {
    PiSerializer<String> serializer =
            PiSerializeServices.requireSerializer(PiSerializers.STRING);
});
```

这适合测试、隔离配置、或者上层包需要一套独立 serializer 策略的场景。

`@PiField(serializer = ...)` 可以为单个字段指定 provider。provider 必须是可访问的具体类，并且有不抛 checked exception 的无参构造器。
