# JSONFold .NET CLI and Benchmark

This ZIP adds two projects on top of the existing `dotnet/` tree:

```text
cli/JsonFold.Cli/
benchmark/JsonFold.Benchmark/
```

Both projects reference the existing library project:

```text
src/JsonFold/JsonFold.csproj
```

## Install into the repo

From the repository root, unzip/copy this archive into the existing `dotnet/` directory so the layout becomes:

```text
dotnet/
    src/JsonFold/
    samples/JsonFold.Sample/
    cli/JsonFold.Cli/
    benchmark/JsonFold.Benchmark/
    JsonFold.sln
```

Then add the projects to the solution:

```bash
dotnet sln dotnet/JsonFold.sln add dotnet/cli/JsonFold.Cli/JsonFold.Cli.csproj
dotnet sln dotnet/JsonFold.sln add dotnet/benchmark/JsonFold.Benchmark/JsonFold.Benchmark.csproj
```

Or, if you are already inside `dotnet/`:

```bash
dotnet sln add cli/JsonFold.Cli/JsonFold.Cli.csproj
dotnet sln add benchmark/JsonFold.Benchmark/JsonFold.Benchmark.csproj
```

## Build everything

```bash
cd dotnet
dotnet restore
dotnet build
```

## Run the full CLI

Show help:

```bash
dotnet run --project cli/JsonFold.Cli -- --help
```

Run demo:

```bash
dotnet run --project cli/JsonFold.Cli -- --demo --compact high --width 120
```

Format a file:

```bash
dotnet run --project cli/JsonFold.Cli -- --compact max --sort-keys input.json > out.json
```

Pipe stdin/stdout:

```bash
cat input.json | dotnet run --project cli/JsonFold.Cli -- --compact default > out.json
```

Use config overrides:

```bash
dotnet run --project cli/JsonFold.Cli -- \
  --compact default \
  --width 120 \
  --pack-array-items 20 \
  --fold-nesting 4 \
  --join-nesting 2 \
  input.json
```

## Publish the CLI as a standalone local executable

Framework-dependent executable:

```bash
dotnet publish cli/JsonFold.Cli -c Release -o build/cli
./build/cli/JsonFold.Cli --help
```

Self-contained Linux x64 executable:

```bash
dotnet publish cli/JsonFold.Cli -c Release -r linux-x64 --self-contained true -o build/cli-linux-x64
./build/cli-linux-x64/JsonFold.Cli --help
```

## Run benchmark

BenchmarkDotNet should be run in Release mode:

```bash
dotnet run -c Release --project benchmark/JsonFold.Benchmark
```

Quick smoke benchmark:

```bash
dotnet run -c Release --project benchmark/JsonFold.Benchmark -- --filter '*JsonFoldDefault*' --warmupCount 1 --iterationCount 3
```

BenchmarkDotNet writes reports under:

```text
benchmark/JsonFold.Benchmark/BenchmarkDotNet.Artifacts/
```
