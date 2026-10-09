# ORM Performance Benchmarks

JMH microbenchmarks for the bx-orm module: ORM entity CRUD and HQL throughput
against an embedded Apache Derby datasource.

The ORM module (and therefore the Hibernate version under test) is loaded from a
module path given by `-Dorm.module.path` (default `./build/module`). The
benchmark's own outer classpath carries only the BoxLang runtime and JMH, exactly
like production, so Hibernate is resolved solely inside the module's isolated
classloader. This keeps a Hibernate 5 vs Hibernate 7 comparison honest.

## Benchmarks

- `ORMCrudBenchmark` — single-row save+load and a small HQL query (I/O-bound; dominated by JDBC/transaction wait).
- `ORMReadBenchmark` — bulk hydration of `rowCount` rows (default 2000) into BoxLang entities. Exercises per-entity/per-property work at volume.
- `ORMBootBenchmark` — cold ORM application boot: entity discovery + mapping generation + Hibernate `SessionFactory` build. `SingleShotTime`; **run one boot per fork** (`-i 1 -wi 0 -f N`), since repeated boots in one JVM accumulate isolated-classloader/metaspace pressure and skew the numbers.

## Running

Benchmark the current build (Hibernate 7):

```bash
./gradlew jmh -PjmhArgs="-f 1 -wi 3 -i 5"
```

Cold boot (one boot per fork):

```bash
./gradlew jmh -PjmhArgs="ORMBootBenchmark -i 1 -wi 0 -f 10"
```

Find hotspots with the sampling profiler (note: BoxRuntime pool threads sit parked, so read the RUNNABLE breakdown, not the WAITING totals):

```bash
./gradlew jmh -PjmhArgs="ORMReadBenchmark.hydrateAll -p rowCount=2000 -prof stack:lines=4;top=25 -f 1 -wi 2 -i 3"
```

Compare the current build (Hibernate 7) against the last Hibernate 5 release
(module `1.7.0`, downloaded and set up automatically):

```bash
./gradlew jmhCompare
```

`jmhCompare` runs each version in its own forked JVM (a single JVM cannot host both
shaded Hibernate versions), writes `build/perf/hib5.json` and `build/perf/hib7.json`,
and prints a side-by-side table.

Rigor is overridable for a quick smoke run:

```bash
./gradlew jmhCompare -PperfForks=1 -PperfWarmup=1 -PperfIterations=2
```

## Notes

- Benchmarks reuse the standalone `src/test/resources/derbyApp` fixture.
- Numbers are only meaningful at default rigor; low iteration counts are noisy.
- Results are machine dependent — always compare the two columns from the same run,
  never a Hib5 number from one machine against a Hib7 number from another.
