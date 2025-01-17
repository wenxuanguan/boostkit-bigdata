/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2021. All rights reserved.
 */

#ifndef THESTRAL_PLUGIN_MASTER_CONCURRENT_MAP_H
#define THESTRAL_PLUGIN_MASTER_CONCURRENT_MAP_H

#include <memory>
#include <mutex>
#include <unordered_map>
#include <utility>
#include <jni.h>

/**
 * An utility class that map module id to module points
 * @tparam Holder class of the object to hold
 */
template <typename Holder>
class ConcurrentMap {
public:
    ConcurrentMap() : module_id_(init_module_id_) {}

    jlong Insert(Holder holder) {
        std::lock_guard<std::mutex> lock(mtx_);
        jlong result = module_id_++;
        map_.insert(std::pair<jlong, Holder>(result, holder));
        return result;
    }

    void Erase(jlong module_id) {
        std::lock_guard<std::mutex> lock(mtx_);
        map_.erase(module_id);
    }

    Holder Lookup(jlong module_id) {
        std::lock_guard<std::mutex> lock(mtx_);
        auto it = map_.find(module_id);
        if (it != map_.end()) {
            return it->second;
        }
        return nullptr;
    }

    void Clear() {
        std::lock_guard<std::mutex> lock(mtx_);
        map_.clear();
    }

    size_t Size() {
        std::lock_guard<std::mutex> lock(mtx_);
        return map_.size();
    }
private:
    // Initialize the module id starting value to a number greater than zero
    // to allow for easier debugging of uninitialized java variables.
    static constexpr int init_module_id_ = 4;

    int64_t module_id_;
    std::mutex mtx_;
    // map from module ids return to Java and module pointers
    std::unordered_map<jlong, Holder> map_;

};

#endif
