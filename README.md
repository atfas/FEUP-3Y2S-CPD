# CPD Projects

Projects for **CPD (Computação Paralela e Distribuída / Parallel and Distributed Computing)** at FEUP, L.EIC 3rd year, 2nd semester (2025/2026). Group T13G11.

## Projects

### [Assignment 1](assign1/) — Matrix multiplication performance

Study of how memory access patterns and parallelism affect the performance of matrix multiplication.

- **Part 1 (single core):** naive, line-by-line and block multiplication in C++ and Java, compared across matrix sizes and block sizes.
- **Part 2 (multi-core):** OpenMP versions of the algorithms (`parallel for`, `collapse`, `simd`, reductions), measured for speedup, efficiency and GFLOPS from 4 to 24 threads.

Source code is in [`assign1/src`](assign1/src) and the results and charts are in [`assign1/doc`](assign1/doc/graphs.md).

### [Assignment 2](assign2/) — Distributed chat system with AI rooms

Client-server chat system in Java SE 21 over TCP.

- User registration, login and token-based sessions
- Multiple chat rooms with real-time messaging
- Automatic reconnection and session recovery after network failures
- AI rooms backed by a local LLM through [Ollama](https://ollama.com/)
- Virtual threads for each client and `ReadWriteLock`-based thread safety

Setup and usage are in the [assignment README](assign2/README.md), with design details in [`assign2/doc`](assign2/doc/README.md).

## Repository structure

```
.
├── assign1/
│   ├── src/    # C++ (sequential and OpenMP) and Java matrix multiplication
│   └── doc/    # measured values and performance charts
└── assign2/
    ├── src/    # chat client, server, shared models/protocol and Makefile
    └── doc/    # architecture and design documentation
```

## Group members

1. Afonso Saraiva (up202304461)
2. Daniel Pinto   (up202304957)
3. Raluca Ioana   (up202512464)
4. Francisco Borralho (201806242)
