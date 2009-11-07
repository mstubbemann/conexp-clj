(ns conexp.io.contexts
  (:use conexp.io.base))

;;; Method Declaration

(defmulti write-context (fn [format ctx file] format))

(let [known-context-input-formats (ref {})]
  (defn add-context-input-format [name predicate]
    (dosync (alter known-context-input-formats assoc name predicate)))

  (defn get-known-context-input-formats []
    (keys @known-context-input-formats))

  (defn find-context-input-format [file]
    (with-open [input-reader (reader file)]
      (let [input-lines (take-while identity (repeatedly #(.readLine input-reader)))]
	(first
	 (for [[name predicate] @known-context-input-formats
	       :when (predicate input-lines)]
	   name))))))

(defmulti read-context find-context-input-format)

(defmethod write-context :default [format _ _]
  (illegal-argument "Format " format " for context output is not known."))

(defmethod read-context :default [file]
  (illegal-argument "Cannot determine format of context in " file))

;;;
;;; Formats
;;;

;; Burmeister Format

(defmethod write-context :burmeister [_ ctx file]
  (with-out-writer file
    (println \B)
    (println)
    (println (count (objects ctx)))
    (println (count (attributes ctx)))
    (println)
    (doseq [g (objects ctx)] (println g))
    (doseq [m (attributes ctx)] (println m))
    (let [inz (incidence ctx)]
      (doseq [g (objects ctx)]
	(doseq [m (attributes ctx)]
	  (print (if (inz [g m]) "X" ".")))
	(println)))))

(add-context-input-format :burmeister
			  (fn [input-lines]
			    (= \B (first (first input-lines)))))

(defmethod read-context :burmeister [file]
  (with-in-reader file
    (let [_                    (get-lines 2)    ; "B\n\n"

	  number-of-objects    (Integer/parseInt (get-line))
	  number-of-attributes (Integer/parseInt (get-line))

	  _                    (get-line)	  ; "\n"

	  seq-of-objects       (get-lines number-of-objects)
	  seq-of-attributes    (get-lines number-of-attributes)]
      (loop [objs seq-of-objects
	     incidence #{}]
	(if (empty? objs)
	  (make-context (set seq-of-objects)
			(set seq-of-attributes)
			incidence)
	  (let [line (get-line)]
	    (recur (rest objs)
		   (union incidence
			  (set-of [(first objs) (nth seq-of-attributes idx-m)]
				  [idx-m (range number-of-attributes)
				   :when (= \X (nth line idx-m))])))))))))

;; Conexp

(add-context-input-format :conexp
			  (fn [input-lines]
			    (let [nonblank-lines (filter #(re-matches #"^.*\S.*$" %) input-lines)]
			      (and (re-matches #"\s*<\?\s*xml.*\?>.*" (first nonblank-lines))
				   (or
				    (re-matches #".*<ConceptualSystem>.*" (first nonblank-lines))
				    (re-matches #".*<ConceptualSystem>.*" (second nonblank-lines)))))))

(defn- find-tags [seq-of-hashes tag]
  (for [hash seq-of-hashes :when (= tag (:tag hash))] hash))

(defn- find-tag [seq-of-hashes tag]
  (first (find-tags seq-of-hashes tag)))

(defn- trim [str]
  (.trim str))

(defn- hash-from-pairs [pairs]
  (apply hash-map (flatten pairs)))

(defmethod read-context :conexp [file]
  (with-in-reader file
    (let [xml-tree (parse-trim *in*)
	  contexts (:content (first (find-tags (:content xml-tree) :Contexts)))]
      (cond
	(= 0 (count contexts))
	(throw (IllegalArgumentException. (str "No context specified in " file)))
	(< 1 (count contexts))
	(throw (IllegalArgumentException. (str "More than one context specified in " file))))
      (let [context (first contexts)
	    atts-map (find-tag (:content context) :Attributes)
	    objs-map (find-tag (:content context) :Objects)

	    obj-idxs-map (hash-from-pairs
			  (for [obj-map (:content objs-map)]
			    [(-> obj-map :content (find-tag :Name) :content first trim)
			     (set-of (get-in att [:attrs :AttributeIdentifier])
				     [att (-> obj-map :content (find-tag :Intent) :content)])]))

	    idx-atts-map (hash-from-pairs
			  (for [att-map (:content atts-map)]
			    [(get-in att-map [:attrs :Identifier])
			     (-> att-map :content (find-tag :Name) :content first trim)]))]
	(make-context (set (keys obj-idxs-map))
		      (set (vals idx-atts-map))
		      (set-of [g (idx-atts-map idx) ]
			      [[g att-idxs] obj-idxs-map
			       idx att-idxs]))))))

(defn- ctx->xml-vector [ctx id]
  (let [ctx-atts (zipmap (attributes ctx) (iterate inc 0))
	ctx-objs (objects ctx)
	attributes (vector :Attributes
			   (map (fn [[att id]]
				  [:Attribute {:Identifier id}
				   [:raw! (str "\n          <Name>" att "</Name>")]])
				ctx-atts))
	objects (vector :Objects
			(for [obj ctx-objs]
			  [:Object
			   [:raw! (str "\n          <Name>" obj "</Name>")]
			   (vector :Intent
				   (for [att (object-derivation ctx #{obj})]
				     [:HasAttribute {:AttributeIdentifier (ctx-atts att)}]))]))]
    [:Context {:Identifier "0", :Type "Binary"}
     attributes
     objects]))

(defmethod write-context :conexp [_ ctx file]
  (binding [clojure.contrib.prxml/*prxml-indent* 2]
    (with-out-writer file
      (prxml [:decl! {:version "1.0"}])
      (prxml [:ConceptualSystem
	      [:Version {:MajorNumber "1", :MinorNumber "0"}]
	      [:Contexts (ctx->xml-vector ctx 0)]]))))