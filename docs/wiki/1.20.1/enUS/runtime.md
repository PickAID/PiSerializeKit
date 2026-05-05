# Serializer Runtime

The serializer runtime connects Java types to concrete read/write logic. If built-in serializers are enough, install the default runtime:

```java
PiSerializeRuntime runtime = new PiSerializeRuntime();
PiBuiltInSerializers.install(runtime);
PiSerializeServices.install(runtime);
```

Use a scoped runtime when one code path needs a temporary override:

```java
PiSerializeServices.withScope(runtime, () -> {
    PiSerializer<String> serializer =
            PiSerializeServices.requireSerializer(PiSerializers.STRING);
});
```

This is useful for tests, isolated configs, or higher-level packs that need their own serializer policy.

`@PiField(serializer = ...)` can attach a provider to one field. The provider must be an accessible concrete class with an accessible no-arg constructor, and that constructor must not throw checked exceptions.
