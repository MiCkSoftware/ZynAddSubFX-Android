#ifndef ZYN_ANDROID_FFTW3_SHIM_H
#define ZYN_ANDROID_FFTW3_SHIM_H

#ifdef __cplusplus
extern "C" {
#endif

typedef float fftwf_real;
typedef float fftwf_complex[2];

typedef struct zyn_fftwf_plan_s *fftwf_plan;

#ifndef FFTW_ESTIMATE
#define FFTW_ESTIMATE (1U << 6)
#endif

fftwf_plan fftwf_plan_dft_r2c_1d(int n,
                                 fftwf_real *in,
                                 fftwf_complex *out,
                                 unsigned flags);

fftwf_plan fftwf_plan_dft_c2r_1d(int n,
                                 fftwf_complex *in,
                                 fftwf_real *out,
                                 unsigned flags);

void fftwf_execute_dft_r2c(const fftwf_plan p, fftwf_real *in, fftwf_complex *out);
void fftwf_execute_dft_c2r(const fftwf_plan p, fftwf_complex *in, fftwf_real *out);

void fftwf_destroy_plan(fftwf_plan p);
void fftwf_cleanup(void);

#ifdef __cplusplus
}
#endif

#endif
