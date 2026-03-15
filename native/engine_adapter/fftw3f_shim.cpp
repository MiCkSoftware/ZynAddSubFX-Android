#include "fftw3.h"

#include <cmath>
#include <complex>
#include <vector>
#include <algorithm>
#include <cstdint>

namespace {

enum class PlanKind {
    R2C,
    C2R,
};

struct PlanImpl {
    int n;
    PlanKind kind;
    bool supported;
    bool radix2;
};

constexpr float kPi = 3.14159265358979323846f;
constexpr int kMaxSafeShimFftSize = 1 << 20; // practical upper bound for this shim

bool isPowerOfTwo(int n) {
    return n > 0 && (static_cast<uint32_t>(n) & (static_cast<uint32_t>(n) - 1u)) == 0u;
}

size_t reverseBits(size_t x, unsigned bits) {
    size_t y = 0;
    for (unsigned i = 0; i < bits; ++i) {
        y = (y << 1u) | (x & 1u);
        x >>= 1u;
    }
    return y;
}

void fftRadix2(std::vector<std::complex<float>> &a, bool inverse) {
    const size_t n = a.size();
    if (n == 0) return;

    unsigned bits = 0;
    while ((1u << bits) < n) ++bits;

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
                const std::complex<float> u = a[i + j];
                const std::complex<float> v = a[i + j + half] * w;
                a[i + j] = u + v;
                a[i + j + half] = u - v;
                w *= wlen;
            }
        }
    }
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
    if (n <= 0) {
        return nullptr;
    }
    auto *p = new zyn_fftwf_plan_s;
    p->impl = {n, PlanKind::R2C, n <= kMaxSafeShimFftSize, isPowerOfTwo(n)};
    return p;
}

fftwf_plan fftwf_plan_dft_c2r_1d(int n,
                                 fftwf_complex * /*in*/,
                                 fftwf_real * /*out*/,
                                 unsigned /*flags*/)
{
    if (n <= 0) {
        return nullptr;
    }
    auto *p = new zyn_fftwf_plan_s;
    p->impl = {n, PlanKind::C2R, n <= kMaxSafeShimFftSize, isPowerOfTwo(n)};
    return p;
}

void fftwf_execute_dft_r2c(const fftwf_plan p, fftwf_real *in, fftwf_complex *out)
{
    if (!p || !in || !out || p->impl.kind != PlanKind::R2C) {
        return;
    }

    const int n = p->impl.n;
    const int bins = n / 2 + 1;
    if (!p->impl.supported || !p->impl.radix2) {
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
    fftRadix2(time, /*inverse=*/false);
    for (int k = 0; k < bins; ++k) {
        out[k][0] = time[static_cast<size_t>(k)].real();
        out[k][1] = time[static_cast<size_t>(k)].imag();
    }
}

void fftwf_execute_dft_c2r(const fftwf_plan p, fftwf_complex *in, fftwf_real *out)
{
    if (!p || !in || !out || p->impl.kind != PlanKind::C2R) {
        return;
    }

    const int n = p->impl.n;
    const int bins = n / 2 + 1;
    if (!p->impl.supported || !p->impl.radix2) {
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

    fftRadix2(spectrum, /*inverse=*/true);
    for (int t = 0; t < n; ++t) {
        // FFTW c2r returns the unnormalized inverse transform.
        out[t] = spectrum[static_cast<size_t>(t)].real();
    }
}

void fftwf_destroy_plan(fftwf_plan p)
{
    delete p;
}

void fftwf_cleanup(void)
{
    // No global state in the shim.
}

} // extern "C"
