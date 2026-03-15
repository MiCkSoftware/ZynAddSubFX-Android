#include "fftw3.h"

#include <algorithm>
#include <cmath>
#include <complex>
#include <cstdint>
#include <vector>

namespace {

enum class PlanKind {
    R2C,
    C2R,
};

struct PlanImpl {
    int n;
    PlanKind kind;
    bool supported;
};

constexpr float kPi = 3.14159265358979323846f;
constexpr int kMaxSupportedFftSize = 1 << 20;

bool isPowerOfTwo(int n) {
    return n > 0 && (static_cast<uint32_t>(n) & (static_cast<uint32_t>(n) - 1u)) == 0u;
}

size_t nextPowerOfTwo(size_t n) {
    size_t p = 1;
    while (p < n) p <<= 1u;
    return p;
}

size_t reverseBits(size_t x, unsigned bits) {
    size_t y = 0;
    for (unsigned i = 0; i < bits; ++i) {
        y = (y << 1u) | (x & 1u);
        x >>= 1u;
    }
    return y;
}

void fftRadix2Inplace(std::vector<std::complex<float>> &a, bool inverse) {
    const size_t n = a.size();
    if (n == 0) return;

    unsigned bits = 0;
    while ((static_cast<size_t>(1) << bits) < n) ++bits;

    for (size_t i = 0; i < n; ++i) {
        const size_t j = reverseBits(i, bits);
        if (j > i) std::swap(a[i], a[j]);
    }

    for (size_t len = 2; len <= n; len <<= 1u) {
        const float ang = (inverse ? 2.0f : -2.0f) * kPi / static_cast<float>(len);
        const std::complex<float> wlen(std::cos(ang), std::sin(ang));
        for (size_t i = 0; i < n; i += len) {
            std::complex<float> w(1.0f, 0.0f);
            const size_t half = len >> 1u;
            for (size_t j = 0; j < half; ++j) {
                const auto u = a[i + j];
                const auto v = a[i + j + half] * w;
                a[i + j] = u + v;
                a[i + j + half] = u - v;
                w *= wlen;
            }
        }
    }
}

// Forward arbitrary-size DFT using radix-2 directly or Bluestein reduction.
void fftForwardAny(std::vector<std::complex<float>> &a) {
    const size_t n = a.size();
    if (n == 0) return;
    if (isPowerOfTwo(static_cast<int>(n))) {
        fftRadix2Inplace(a, /*inverse=*/false);
        return;
    }

    const size_t m = nextPowerOfTwo(n * 2u - 1u);
    std::vector<std::complex<float>> A(m, {0.0f, 0.0f});
    std::vector<std::complex<float>> B(m, {0.0f, 0.0f});

    for (size_t i = 0; i < n; ++i) {
        const float angle = kPi * static_cast<float>((i * i) % (2u * n)) / static_cast<float>(n);
        const std::complex<float> w_neg(std::cos(angle), -std::sin(angle)); // exp(-i*pi*i^2/n)
        const std::complex<float> w_pos(std::cos(angle), std::sin(angle));  // exp(+i*pi*i^2/n)
        A[i] = a[i] * w_neg;
        B[i] = w_pos;
        if (i != 0) {
            B[m - i] = w_pos;
        }
    }

    fftRadix2Inplace(A, /*inverse=*/false);
    fftRadix2Inplace(B, /*inverse=*/false);
    for (size_t i = 0; i < m; ++i) {
        A[i] *= B[i];
    }
    fftRadix2Inplace(A, /*inverse=*/true);

    const float invM = 1.0f / static_cast<float>(m); // radix2 inverse is unnormalized
    for (size_t k = 0; k < n; ++k) {
        const float angle = kPi * static_cast<float>((k * k) % (2u * n)) / static_cast<float>(n);
        const std::complex<float> w_neg(std::cos(angle), -std::sin(angle)); // exp(-i*pi*k^2/n)
        a[k] = (A[k] * invM) * w_neg;
    }
}

void fftInverseAny(std::vector<std::complex<float>> &a) {
    // Unnormalized inverse via conjugation trick: IFFT(x) = conj(FFT(conj(x)))
    for (auto &v : a) v = std::conj(v);
    fftForwardAny(a);
    for (auto &v : a) v = std::conj(v);
}

} // namespace

struct zyn_fftwf_plan_s {
    PlanImpl impl;
};

extern "C" {

fftwf_plan fftwf_plan_dft_r2c_1d(int n,
                                 fftwf_real * /*in*/,
                                 fftwf_complex * /*out*/,
                                 unsigned /*flags*/)
{
    if (n <= 0) return nullptr;
    auto *p = new zyn_fftwf_plan_s;
    p->impl = {n, PlanKind::R2C, n <= kMaxSupportedFftSize};
    return p;
}

fftwf_plan fftwf_plan_dft_c2r_1d(int n,
                                 fftwf_complex * /*in*/,
                                 fftwf_real * /*out*/,
                                 unsigned /*flags*/)
{
    if (n <= 0) return nullptr;
    auto *p = new zyn_fftwf_plan_s;
    p->impl = {n, PlanKind::C2R, n <= kMaxSupportedFftSize};
    return p;
}

void fftwf_execute_dft_r2c(const fftwf_plan p, fftwf_real *in, fftwf_complex *out)
{
    if (!p || !in || !out || p->impl.kind != PlanKind::R2C) return;

    const int n = p->impl.n;
    const int bins = n / 2 + 1;
    if (!p->impl.supported) {
        for (int k = 0; k < bins; ++k) {
            out[k][0] = 0.0f;
            out[k][1] = 0.0f;
        }
        return;
    }

    std::vector<std::complex<float>> time(static_cast<size_t>(n));
    for (int i = 0; i < n; ++i) {
        time[static_cast<size_t>(i)] = {in[i], 0.0f};
    }
    fftForwardAny(time);
    for (int k = 0; k < bins; ++k) {
        out[k][0] = time[static_cast<size_t>(k)].real();
        out[k][1] = time[static_cast<size_t>(k)].imag();
    }
}

void fftwf_execute_dft_c2r(const fftwf_plan p, fftwf_complex *in, fftwf_real *out)
{
    if (!p || !in || !out || p->impl.kind != PlanKind::C2R) return;

    const int n = p->impl.n;
    const int bins = n / 2 + 1;
    if (!p->impl.supported) {
        std::fill(out, out + n, 0.0f);
        return;
    }

    std::vector<std::complex<float>> spectrum(static_cast<size_t>(n), {0.0f, 0.0f});
    for (int k = 0; k < bins; ++k) {
        spectrum[static_cast<size_t>(k)] = {in[k][0], in[k][1]};
    }
    for (int k = 1; k < n / 2; ++k) {
        spectrum[static_cast<size_t>(n - k)] = std::conj(spectrum[static_cast<size_t>(k)]);
    }

    fftInverseAny(spectrum); // unnormalized inverse (FFTW-compatible c2r semantics)
    for (int t = 0; t < n; ++t) {
        out[t] = spectrum[static_cast<size_t>(t)].real();
    }
}

void fftwf_destroy_plan(fftwf_plan p)
{
    delete p;
}

void fftwf_cleanup(void)
{
    // no global state
}

} // extern "C"

