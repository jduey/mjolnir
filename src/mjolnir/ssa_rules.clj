(ns mjolnir.ssa-rules
  (:require [clojure.pprint  :refer [pprint]]))

(def rules (atom []))

(defmacro defrule [name args doc & body]
  (println "Registered rule" name )
  (swap! rules conj `[(~name ~@args)
                      ~@body])
  nil)

;; Utility

(defrule global-def [?id ?name ?type]
  "Functions are global entities"
  [?id :node/type :node.type/fn]
  [?id :fn/name ?name]
  [?id :fn/type ?type])


;; Inference Rules

(defrule infer-node [?id ?attr ?val]
  "infer return-types"
  (return-type ?id ?val)
  [(identity :node/return-type) ?attr])

(defrule infer-node [?id ?attr ?val]
  "infer binop subtypes"
  (infer-binop ?id ?val)
  [(identity :inst.binop/sub-type) ?attr])


;;


(defrule return-type [?id ?type]
  "Anything with :node/return-type returns that type"
  [?id :node/return-type ?type])

(defrule return-type [?id ?type]
  "Consts return their given type, if it exists"
  [?id :inst/type :inst.type/const]
  [?id :const/type ?type])

(defrule return-type [?id ?type]
  "Binops return the same type as their args"
  [?id :inst/type :inst.type/binop]
  [?id :inst.arg/arg0 ?arg0]
  [?id :inst.arg/arg1 ?arg1]
  (return-type ?arg0 ?type)
  (return-type ?arg1 ?type))

(defrule return-type [?id ?type]
  "Cmp operations always return Int1"
  [?id :inst/type :inst.type/cmp]
  [?id :node/return-type ?type])

(defrule return-type [?id ?type]
  "Phi nodes always return the return type of one of their values"
  [?phi :phi.value/value ?arg]
  [?phi :phi.value/node ?id]
  (return-type ?arg ?type))

(defrule return-type [?id ?type]
  "Globals return the type of the matching global"
  [?id :inst/type :inst.type/gbl]
  [?id :inst.gbl/name ?name]
  (global-def ?gbl ?name ?type))

(defrule return-type [?id ?type]
  "Function calls return the return type of the function they are calling"
  [?id :inst/type :inst.type/call]
  [?id :inst.call/fn ?fn-src]
  (return-type ?fn-src ?fn-t)
  [?fn-t :type.fn/return ?type])

(defrule return-type [?id ?type]
  "Arg instructions return the type from the function type"
  [?id :inst/type :inst.type/arg]
  [?id :inst/block ?block]
  [?block :block/fn ?fn]
  [?fn :fn/type ?fn-t]
  [?arg-node :fn.arg/fn ?fn-t]
  [?id :inst.arg/idx ?idx]
  [?arg-node :fn.arg/idx ?idx]
  [?arg-node :fn.arg/type ?type])

(defn debug-datalog [x]
  (println "datalong -----------------   " x)
  x)

(defrule return-type [?id ?type]
  "ASet returns the arr type"
  [?id :inst/type :inst.type/aset]
  [?id :inst.arg/arg0 ?arg0]
  [?arg0 :inst/type ?v]
  [(mjolnir.ssa-rules/debug-datalog ?v)]
  (return-type ?arg0 ?type)
)

(defrule return-type [?id ?type]
  "AGet returns the element type"
  [?id :inst/type :inst.type/aget]
  [?id :inst.arg/arg0 ?arg0]
  (return-type ?arg0 ?arg0-t)
  [?arg0-t :type/element-type ?type])


(defrule validate [?id ?msg]
  "Binops must have the same types for all args"
  [?id :inst/type :inst.type/binop]
  [?id :inst.arg/arg0 ?arg0]
  [?id :inst.arg/arg1 ?arg1]
  (return-type ?arg0 ?arg0-tp)
  (return-type ?arg1 ?arg1-tp)
  #_(return-type ?id ?this-tp)
  [(not= ?arg0-tp ?arg1-tp)]
  [(identity "Binop args must match return type") ?msg])



;; Binop rules - These rules define an attribute that helps emitters
;; decide if a binop is a Float or Int operation. FMul is different
;; from IMul, so this code specializes that information. 

(defrule infer-binop [?id ?op]
  "A binop of two ints "
  [?id :inst/type :inst.type/binop]
  [?id :inst.arg/arg0 ?arg0]
  [?id :inst.arg/arg1 ?arg1]
  (return-type ?arg0 ?arg0-t)
  (return-type ?arg1 ?arg1-t)
  (binop-subtype ?id ?arg0-t ?arg1-t ?op))

(def binop-int-translation
  {:inst.binop.type/add :inst.binop.subtype/iadd
   :inst.binop.type/sub :inst.binop.subtype/isub
   :inst.binop.type/mul :inst.binop.subtype/imul
   :inst.binop.type/div :inst.binop.subtype/idiv
   :inst.binop.type/rem :inst.binop.subtype/irem})

(def binop-float-translation
  {:inst.binop.type/add :inst.binop.subtype/fadd
   :inst.binop.type/sub :inst.binop.subtype/fsub
   :inst.binop.type/mul :inst.binop.subtype/fmul
   :inst.binop.type/div :inst.binop.subtype/fdiv
   :inst.binop.type/rem :inst.binop.subtype/frem})

(defrule binop-subtype [?type ?arg0-t ?arg1-t ?op]
  "Int + resolves to :iadd"
  [?arg0-t :node/type :type/int]
  [?arg1-t :node/type :type/int]
  [?type :inst.binop/type ?binop]
  [(mjolnir.ssa-rules/binop-int-translation ?binop) ?op])

(defrule binop-subtype [?type ?arg0-t ?arg1-t ?op]
  "Int + resolves to :iadd"
  [?arg0-t :node/type :type/float]
  [?arg1-t :node/type :type/float]
  [?type :inst.binop/type ?binop]
  [(mjolnir.ssa-rules/binop-float-translation ?binop) ?op])



