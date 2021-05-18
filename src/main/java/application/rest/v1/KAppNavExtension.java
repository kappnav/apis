/*
 * Copyright 2019 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package application.rest.v1;

public class KAppNavExtension {

    public static void init() {
        ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.put("Liberty-App", "kappnav.io/v1beta1");
        ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.put("WAS-Traditional-App", "kappnav.io/v1beta1");
        ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.put("Liberty-Collective", "kappnav.io/v1beta1");
        ComponentInfoRegistry.CORE_KIND_TO_API_VERSION_MAP.put("WAS-ND-Cell", "kappnav.io/v1beta1");
    }
}