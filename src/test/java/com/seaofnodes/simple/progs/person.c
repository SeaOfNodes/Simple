#include <stdio.h>
#include <stdbool.h>
#include <stdlib.h>

typedef struct Person {
    int age;
} Person;

typedef struct PersonArray {
    unsigned int length;
    int pad;
    Person* data[];
} PersonArray;

__attribute__ ((CALL_CONV))
extern void fcn(PersonArray* people, int index);

int main() {
    Person base[5] = {{25},{1},{18},{45},{60}};
    unsigned char buf[8+8*5];
    PersonArray* people_array = (PersonArray*)buf;
    people_array->length=5;
    for (int i=0; i<people_array->length; i++) people_array->data[i] = &base[i];

    fcn(people_array, 1);
    fcn(people_array, 1);
    fcn(people_array, 1);
    fcn(people_array, 1);
    fcn(people_array, 1);

    printf("%d\n", people_array->data[1]->age); // expected: 6

    return 0;
}