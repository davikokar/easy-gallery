// JNI wrapper that runs dlib's face-recognition ResNet on one 150x150 RGB chip.
// The network type is dlib's own (python binding face_recognition.cpp / dnn_face_recognition_ex.cpp, Boost licence).
#include <jni.h>
#include <dlib/dnn.h>
#include <string>

using namespace dlib;

template <template <int,template<typename>class,int,typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual = add_prev1<block<N,BN,1,tag1<SUBNET>>>;

template <template <int,template<typename>class,int,typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual_down = add_prev2<avg_pool<2,2,2,2,skip1<tag2<block<N,BN,2,tag1<SUBNET>>>>>>;

template <int N, template <typename> class BN, int stride, typename SUBNET>
using block  = BN<con<N,3,3,1,1,relu<BN<con<N,3,3,stride,stride,SUBNET>>>>>;

template <int N, typename SUBNET> using ares      = relu<residual<block,N,affine,SUBNET>>;
template <int N, typename SUBNET> using ares_down = relu<residual_down<block,N,affine,SUBNET>>;

template <typename SUBNET> using alevel0 = ares_down<256,SUBNET>;
template <typename SUBNET> using alevel1 = ares<256,ares<256,ares_down<256,SUBNET>>>;
template <typename SUBNET> using alevel2 = ares<128,ares<128,ares_down<128,SUBNET>>>;
template <typename SUBNET> using alevel3 = ares<64,ares<64,ares<64,ares_down<64,SUBNET>>>>;
template <typename SUBNET> using alevel4 = ares<32,ares<32,ares<32,SUBNET>>>;

using anet_type = loss_metric<fc_no_bias<128,avg_pool_everything<
                            alevel0<alevel1<alevel2<alevel3<alevel4<
                            max_pool<3,3,2,2,relu<affine<con<32,7,7,2,2,
                            input_rgb_image_sized<150>
                            >>>>>>>>>>>>;

extern "C" {

JNIEXPORT jlong JNICALL Java_com_davide_seddio_facespike_DlibNative_load(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    std::string file(p);
    env->ReleaseStringUTFChars(path, p);
    try {
        auto* net = new anet_type();
        deserialize(file) >> *net;
        return reinterpret_cast<jlong>(net);
    } catch (const std::exception& e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
        return 0;
    }
}

JNIEXPORT jfloatArray JNICALL Java_com_davide_seddio_facespike_DlibNative_embed(JNIEnv* env, jobject, jlong handle, jbyteArray rgb) {
    auto* net = reinterpret_cast<anet_type*>(handle);
    jbyte* px = env->GetByteArrayElements(rgb, nullptr);
    matrix<rgb_pixel> img(150, 150);
    for (long r = 0; r < 150; ++r) {
        for (long c = 0; c < 150; ++c) {
            const jbyte* q = px + (r * 150 + c) * 3;
            img(r, c) = rgb_pixel(static_cast<unsigned char>(q[0]), static_cast<unsigned char>(q[1]), static_cast<unsigned char>(q[2]));
        }
    }
    env->ReleaseByteArrayElements(rgb, px, JNI_ABORT);
    matrix<float,0,1> d = (*net)(img);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(d.size()));
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(d.size()), &d(0));
    return out;
}

JNIEXPORT void JNICALL Java_com_davide_seddio_facespike_DlibNative_release(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<anet_type*>(handle);
}

}
