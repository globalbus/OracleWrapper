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