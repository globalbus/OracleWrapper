Oracle Procedure Wrapper
=========
Small library to call Oracle Database procedures and map database objects to/from java objects based on internal Oracle JDBC Driver API.

Uses [spring-jdbc](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/jdbc.html) 
for procedure call and [dalesbred](https://github.com/EvidentSolutions/dalesbred) parts for reflection scanning.

### Define Wrapper
Define Spring JdbcTemplate and InstantiatorProvider (one component per database connection) 

Entry point is ProcedureWrapperBuilder class, a fluent builder for every procedure.

    ProcedureWrapperBuilder<Complex> wrapperBuilder = new ProcedureWrapperBuilder<>();
    
    wrapper = wrapperBuilder.jdbcTemplate(template)
    .instantiatorWrapper(instantion)
    .procedureName(PROCEDURE_NAME)
    .clazz(Complex.class)
    .parameter(SqlStructParameter.createOutArray(OUTPUT_PARAM, Complex.class,
        "array_of_complex"))
    .parameter(SqlStructParameter.createIn(INPUT_PARAM, Complex.class))
    .build();
    
For first, pass 3 required things. Previously defined JdbcTemplate and InstantiatorProvider and Oracle procedure name
For second, set clazz which is a result of procedure (procedure with a single output parameter). 
If you need to map a multiple outputs, you need to extend StoredProcedureWrapper class and override mapObject() method

    private static class PolicyWrapper extends StoredProcedureWrapper<GetPolicyResponse> {

        PolicyWrapper(ProcedureWrapperBuilder<GetPolicyResponse>.BuilderData builderData) {
            super(builderData);
        }

        @Override
        public GetPolicyResponse mapObject(Object... input) {
            List<Object> mappedObjects = Stream.of(input).map(instantiatorWrapper::valueToDatabase)
                .collect(Collectors.toList());
            Map<String, Object> results = internal.execute(mappedObjects.toArray());

            Object output = results.get(OUTPUT_1);
            BigDecimal totalNumber = (BigDecimal) results.get(OUTPUT_2);

            if (output == null || totalNumber == null || BigDecimal.ZERO.compareTo(totalNumber) == 0) {
                return new GetPolicyResponse(null, new BigDecimal(0));
            }

            List<PolicyDetails> list = instantiatorWrapper.getOutputList(PolicyDetails.class, output);
            return new GetPolicyResponse(list, totalNumber);
        }
    }

To use this custom wrapper, pass a method reference or define lambda for initializer() method in ProcedureWrapperBuilder. 

Last step is to define input and outputs for procedure. Inputs can be simple types (like VARCHAR or NUMERIC) 
or Oracle Structs and Oracle Arrays (and Arrays of Structs and so on).
Inputs and outputs must be defined in order as in procedure declaration.
For simple types, you can use SqlParameter and SqlOutParameter directly from Spring JDBC. For Complex types, use SqlStructParameter Helper.
DTO classes for object mapping should be annotated with OracleStruct annotation. Oracle struct name can be defined in here.

## Usage

I have wrapper, what now?
build() method on ProcedureWrapperBuilder returns LazyInitializer. ProcedureWrapper requires connection to gather information about types in database.
Sometimes connection to the database was unavailable and throwing exception on your application startup is a really bad thing. Errors in wrapper definition are visible on wrapper first call.

after all, usage is really simple, there is two methods, mapObject (for returning single object) and mapList for returning Lists. 

    public List<Complex> getList(Complex input) {
         return wrapper.get().mapList(input);
    }