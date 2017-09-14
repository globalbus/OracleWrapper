create or replace TYPE Complex AS OBJECT (
  rpart REAL,  -- "real" attribute
  ipart REAL  -- "imaginary" attribute
);

create or replace TYPE array_of_complex AS varray(10) OF complex;

create or replace PROCEDURE example_proc(outParam OUT array_of_complex, inParam IN Complex)
IS
BEGIN
  outParam:=array_of_complex();
  outParam.EXTEND (2);
  outParam(1):=inParam;
  outParam(2):=inParam;
  --outParam := inParam;--inParam;
  --outParam.rpart:=inParam.rpart+3;
END;

CREATE OR REPLACE TYPE "Message" AS OBJECT
(
   "code" VARCHAR2(15),
   "message" VARCHAR2(15),
   "unrelated" COMPLEX
);

create or replace TYPE "Response" AS OBJECT
(
  "message" "Message"
)

create or replace PROCEDURE test(inParam IN Complex, outParam OUT "Response")
IS
  m "Message";
BEGIN
  m := "Message"('T', 'B', null);
  outParam := "Response"(m);
END;