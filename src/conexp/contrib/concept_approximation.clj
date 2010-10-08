;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.contrib.concept-approximation
  (:use conexp.main))

(ns-doc "Concept Approximation as described by C. Meschke.")

;;;

(defn explore-approximations
  "Performs concept approximation exploration and returns the final
  context."
  [context]
  (let [att-explored-ctx (:context (explore-attributes context)),
        obj-explored-ctx (dual-context
                          (:context (explore-attributes
                                     (dual-context context))))]
    (context-subposition
     (context-apposition context obj-explored-ctx)
     (context-apposition att-explored-ctx
                         (smallest-bond att-explored-ctx
                                        obj-explored-ctx
                                        (union (incidence att-explored-ctx)
                                               (incidence obj-explored-ctx)))))))

;;;

nil